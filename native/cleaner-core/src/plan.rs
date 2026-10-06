use std::ffi::CString;

use crate::consts;
use crate::env::Backend;
use crate::fsmeta;
use crate::rule::{self, Rule};

struct Bind {
    source_view: String,
    source_lower: String,
    target_rel: String,
    create_source: bool,
}

pub struct MountPlan {
    pub view_root: CString,
    pub sources: Vec<CString>,
    pub targets: Vec<CString>,
    pub labels: Vec<String>,
}

const SHARE_BUDGET: usize = 4096;

pub fn build(uid: u32, package: &str, rule: &Rule, backend: &Backend) -> Result<MountPlan, String> {
    let user = uid / consts::PER_USER_RANGE;
    let view_root = format!("/storage/emulated/{user}");
    let view_base = match backend {
        Backend::Fuse => format!("/mnt/user/{user}/emulated/{user}"),
        Backend::Sdcardfs => format!("/mnt/runtime/write/emulated/{user}"),
    };
    let lower_base = format!("/data/media/{user}");
    let isolated_rel = rule.location.isolated_relative_root(package);

    let package_owner = match backend {
        Backend::Fuse => Some((uid, consts::AID_EXT_DATA_RW)),
        Backend::Sdcardfs => None,
    };
    let package_dir = format!("{lower_base}/Android/data/{package}");
    if !fsmeta::make_dir_tree(&package_dir, package_owner) {
        return Err(format!("prepare {package_dir}"));
    }
    if !fsmeta::make_dir_tree(&format!("{lower_base}/{isolated_rel}"), None) {
        return Err(format!("prepare {isolated_rel}"));
    }

    let real = |rel: &str, create: bool, target: String| Bind {
        source_view: format!("{view_base}/{rel}"),
        source_lower: format!("{lower_base}/{rel}"),
        target_rel: target,
        create_source: create,
    };

    let root_base = match backend {
        Backend::Fuse => lower_base.as_str(),
        Backend::Sdcardfs => view_base.as_str(),
    };
    let mut binds: Vec<Bind> = Vec::new();
    binds.push(Bind {
        source_view: format!("{root_base}/{isolated_rel}"),
        source_lower: format!("{lower_base}/{isolated_rel}"),
        target_rel: String::new(),
        create_source: false,
    });
    binds.push(real("Android", false, String::from("Android")));
    for folder in &rule.accessible {
        push_unique(&mut binds, real(folder, true, String::from(folder.as_str())));
    }
    if rule.gallery {
        for folder in consts::GALLERY_FOLDERS {
            push_unique(&mut binds, real(folder, false, String::from(folder)));
        }
    }
    for export in &rule.exports {
        push_unique(&mut binds, real(&export.real, true, String::from(export.view.as_str())));
    }
    for folder in &rule.shared {
        let other_root = rule::isolated_root_of(user, &folder.package);
        let source_lower = format!("{lower_base}/{other_root}/{}", folder.path);
        let mut budget = SHARE_BUDGET;
        fsmeta::open_up_tree(&source_lower, &mut budget);
        push_unique(
            &mut binds,
            Bind {
                source_view: format!("{lower_base}/{other_root}/{}", folder.path),
                source_lower,
                target_rel: String::from(folder.path.as_str()),
                create_source: false,
            },
        );
    }

    binds.sort_by_key(|bind| depth(&bind.target_rel));

    let mut sources = Vec::with_capacity(binds.len());
    let mut targets = Vec::with_capacity(binds.len());
    let mut labels = Vec::with_capacity(binds.len());
    let mut planned: Vec<&Bind> = Vec::with_capacity(binds.len());
    for (index, bind) in binds.iter().enumerate() {
        if bind.create_source {
            fsmeta::make_dir_tree(&bind.source_lower, None);
        }
        if !lower_exists(&bind.source_lower) {
            if index == 0 {
                return Err(String::from("isolated root missing"));
            }
            continue;
        }
        if index > 0 && !prepare_mount_point(&planned, bind) {
            continue;
        }
        let target = if bind.target_rel.is_empty() {
            String::from(view_root.as_str())
        } else {
            format!("{view_root}/{}", bind.target_rel)
        };
        let (Ok(source), Ok(target)) = (CString::new(bind.source_view.as_str()), CString::new(target)) else {
            continue;
        };
        planned.push(bind);
        sources.push(source);
        targets.push(target);
        labels.push(if bind.target_rel.is_empty() {
            String::from("/")
        } else {
            String::from(bind.target_rel.as_str())
        });
    }

    let view_root = CString::new(view_root).map_err(|_| String::from("bad view root"))?;
    Ok(MountPlan { view_root, sources, targets, labels })
}

fn push_unique(binds: &mut Vec<Bind>, bind: Bind) {
    if bind.target_rel.is_empty() || is_within(&bind.target_rel, "Android") {
        return;
    }
    binds.retain(|existing| existing.target_rel != bind.target_rel);
    binds.push(bind);
}

fn prepare_mount_point(earlier: &[&Bind], bind: &Bind) -> bool {
    let Some(cover) = earlier
        .iter()
        .rev()
        .find(|outer| is_within(&bind.target_rel, &outer.target_rel))
    else {
        return false;
    };
    let remainder = if cover.target_rel.is_empty() {
        bind.target_rel.as_str()
    } else {
        bind.target_rel[cover.target_rel.len()..].trim_start_matches('/')
    };
    fsmeta::make_dir_tree(&format!("{}/{remainder}", cover.source_lower), None)
}

fn is_within(path: &str, ancestor: &str) -> bool {
    ancestor.is_empty()
        || path == ancestor
        || (path.starts_with(ancestor) && path.as_bytes().get(ancestor.len()) == Some(&b'/'))
}

fn depth(rel: &str) -> usize {
    if rel.is_empty() {
        0
    } else {
        rel.split('/').count()
    }
}

fn lower_exists(path: &str) -> bool {
    CString::new(path).map(|p| fsmeta::exists(&p)).unwrap_or(false)
}
