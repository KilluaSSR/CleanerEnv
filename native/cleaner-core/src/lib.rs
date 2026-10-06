pub mod consts;
pub mod log;
pub mod policy;
pub mod rule;

#[cfg(target_os = "android")]
pub mod env;
#[cfg(target_os = "android")]
pub mod fsmeta;
#[cfg(target_os = "android")]
pub mod mount;
#[cfg(target_os = "android")]
pub mod mountinfo;
#[cfg(target_os = "android")]
pub mod plan;
#[cfg(target_os = "android")]
pub mod state;

#[cfg(target_os = "android")]
pub use engine::isolate;

#[cfg(target_os = "android")]
mod engine {
    use crate::mount::{self, MountOutcome};
    use crate::rule::Rule;
    use crate::{env, plan, state};

    pub fn isolate(pid: i32, uid: u32, package: &str, rule: &Rule) -> bool {
        let backend = env::backend();
        let (ok, message) = match plan::build(uid, package, rule, &backend) {
            Err(reason) => (false, reason),
            Ok(plan) => match mount::apply(pid, &plan) {
                MountOutcome::Mounted(count) => {
                    (true, format!("{count}/{} binds: {}", plan.sources.len(), plan.labels.join(",")))
                }
                MountOutcome::Failed(reason) => (false, reason),
            },
        };
        if ok {
            crate::log_i!("isolated {package} pid={pid}: {message}");
        } else {
            crate::log_e!("isolation of {package} pid={pid} failed: {message}");
        }
        state::append_mount_log(pid, uid, package, ok, &message);
        ok
    }
}
