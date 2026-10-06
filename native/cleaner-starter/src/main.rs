use std::ffi::CString;
use std::os::raw::c_char;
use std::process::exit;

const SERVER_NICE_NAME: &str = "cleaner_server";
const SERVER_MAIN_CLASS: &str = "killua.dev.cleaner.server.ServerMain";

fn fail(reason: &str) -> ! {
    println!("Failure: {reason}");
    exit(1);
}

fn main() {
    let apk = std::env::args()
        .find_map(|arg| arg.strip_prefix("--apk=").map(String::from))
        .unwrap_or_else(|| fail("missing --apk=<path>"));

    if unsafe { libc::getuid() } != 0 {
        fail("not running as root");
    }
    if std::fs::metadata(&apk).is_err() {
        fail("apk not readable");
    }

    join_init_mount_namespace();
    kill_previous_servers();
    daemonize();
    exec_server(&apk);
}

fn join_init_mount_namespace() {
    let Ok(file) = std::fs::File::open("/proc/1/ns/mnt") else {
        return;
    };
    use std::os::fd::AsRawFd;
    // nstype 0: the fd already identifies a mount namespace.
    unsafe {
        libc::setns(file.as_raw_fd(), 0);
    }
}

fn kill_previous_servers() {
    let me = std::process::id() as i32;
    let Ok(entries) = std::fs::read_dir("/proc") else {
        return;
    };
    for entry in entries.flatten() {
        let Some(pid) = entry.file_name().to_str().and_then(|name| name.parse::<i32>().ok()) else {
            continue;
        };
        if pid == me {
            continue;
        }
        let Ok(cmdline) = std::fs::read(format!("/proc/{pid}/cmdline")) else {
            continue;
        };
        let name = cmdline.split(|b| *b == 0).next().unwrap_or_default();
        if name == SERVER_NICE_NAME.as_bytes() {
            unsafe {
                libc::kill(pid, libc::SIGKILL);
            }
        }
    }
}

fn daemonize() {
    match unsafe { libc::fork() } {
        -1 => fail("fork"),
        0 => {}
        _ => {
            println!("Success: server starting");
            exit(0);
        }
    }
    unsafe {
        libc::setsid();
    }
    match unsafe { libc::fork() } {
        -1 => exit(1),
        0 => {}
        _ => exit(0),
    }
    unsafe {
        libc::chdir(c"/".as_ptr());
        let null = libc::open(c"/dev/null".as_ptr(), libc::O_RDWR);
        if null >= 0 {
            libc::dup2(null, 0);
            libc::dup2(null, 1);
            libc::dup2(null, 2);
            if null > 2 {
                libc::close(null);
            }
        }
    }
}

fn exec_server(apk: &str) -> ! {
    let class_path = format!("-Djava.class.path={apk}");
    let nice_name = format!("--nice-name={SERVER_NICE_NAME}");
    let args: Vec<CString> = [
        "/system/bin/app_process",
        class_path.as_str(),
        "/system/bin",
        nice_name.as_str(),
        SERVER_MAIN_CLASS,
    ]
    .iter()
    .filter_map(|arg| CString::new(*arg).ok())
    .collect();
    let mut argv: Vec<*const c_char> = args.iter().map(|arg| arg.as_ptr()).collect();
    argv.push(std::ptr::null());

    if let Ok(class_path_env) = CString::new(apk) {
        unsafe {
            libc::setenv(c"CLASSPATH".as_ptr(), class_path_env.as_ptr(), 1);
        }
    }
    unsafe {
        libc::execv(argv[0], argv.as_ptr());
    }
    exit(1);
}
