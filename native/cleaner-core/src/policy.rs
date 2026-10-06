use crate::rule::Rule;

pub struct PolicyEntry<'a> {
    pub uid: u32,
    pub package: &'a str,
    pub rule: &'a Rule,
}

pub fn encode(entries: &[PolicyEntry<'_>]) -> String {
    let mut out = String::new();
    for entry in entries.iter().filter(|e| e.rule.enabled) {
        let rule = entry.rule;
        let user = entry.uid / crate::consts::PER_USER_RANGE;
        out.push_str(&format!("uid={}\npkg={}\n", entry.uid, entry.package));
        out.push_str(if rule.providers { "providers=1\n" } else { "providers=0\n" });
        out.push_str(if rule.gallery { "gallery=1\n" } else { "gallery=0\n" });
        out.push_str(&format!("root={}\n", rule.location.isolated_relative_root(entry.package)));
        for path in &rule.accessible {
            out.push_str(&format!("accessible={path}\n"));
        }
        for export in &rule.exports {
            out.push_str(&format!("export={}\t{}\n", export.view, export.real));
        }
        for folder in &rule.shared {
            let source = crate::rule::isolated_root_of(user, &folder.package);
            out.push_str(&format!("shared={}\t{source}/{}\n", folder.path, folder.path));
        }
    }
    out
}

#[cfg(target_os = "android")]
pub fn load_user(user_id: u32) -> String {
    use std::os::unix::fs::MetadataExt;

    use crate::{consts, rule};

    let dir = format!("{}/{}", consts::RULES_DIR, user_id);
    let Ok(listing) = std::fs::read_dir(dir) else {
        return String::new();
    };
    let mut packages: Vec<String> = listing
        .filter_map(|entry| entry.ok()?.file_name().into_string().ok())
        .filter_map(|name| name.strip_suffix(consts::RULE_FILE_SUFFIX).map(String::from))
        .filter(|package| rule::is_valid_package(package))
        .collect();
    packages.sort();

    let mut loaded: Vec<(u32, String, Rule)> = Vec::new();
    for package in packages {
        let Some(rule) = Rule::load_enabled(user_id, &package) else {
            continue;
        };
        if let Ok(meta) = std::fs::metadata(format!("/data/user_de/{user_id}/{package}")) {
            loaded.push((meta.uid(), package, rule));
        }
    }
    let entries: Vec<PolicyEntry<'_>> = loaded
        .iter()
        .map(|(uid, package, rule)| PolicyEntry { uid: *uid, package, rule })
        .collect();
    encode(&entries)
}

pub fn policy_file(user_id: u32) -> String {
    format!("{}/policy", guard_dir(user_id))
}

fn guard_dir(user_id: u32) -> String {
    format!("/data/media/{user_id}/Android/data/{}/guard", crate::consts::APPLICATION_ID)
}

#[cfg(target_os = "android")]
pub fn publish(user_id: u32) -> bool {
    use std::io::Write;
    use std::os::unix::fs::{MetadataExt, OpenOptionsExt, PermissionsExt};

    use crate::{consts, fsmeta};

    let text = load_user(user_id);
    let target = policy_file(user_id);
    if std::fs::read_to_string(&target).is_ok_and(|current| current == text) {
        return true;
    }
    let owner = std::fs::metadata(format!("/data/user_de/{user_id}/{}", consts::APPLICATION_ID))
        .map(|meta| meta.uid())
        .unwrap_or(consts::AID_MEDIA_RW);
    let package_dir = format!("/data/media/{user_id}/Android/data/{}", consts::APPLICATION_ID);
    if !fsmeta::make_dir_tree(&package_dir, Some((owner, consts::AID_EXT_DATA_RW)))
        || !fsmeta::make_dir_tree(&guard_dir(user_id), Some((owner, consts::AID_EXT_DATA_RW)))
    {
        crate::log_e!("policy: cannot create {}", guard_dir(user_id));
        return false;
    }
    let staging = format!("{target}.tmp");
    let written = std::fs::OpenOptions::new()
        .write(true)
        .create(true)
        .truncate(true)
        .mode(0o640)
        .open(&staging)
        .and_then(|mut file| {
            file.write_all(text.as_bytes())?;
            file.set_permissions(std::fs::Permissions::from_mode(0o640))?;
            std::os::unix::fs::fchown(&file, Some(owner), Some(consts::AID_EXT_DATA_RW))?;
            file.sync_all()
        })
        .and_then(|()| std::fs::rename(&staging, &target));
    if let Err(error) = &written {
        crate::log_e!("policy: writing {target} failed: {error}");
    }
    written.is_ok()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn lists_every_enabled_rule() {
        let guarded = Rule::parse(
            "enabled=1\nproviders=1\ngallery=1\naccessible=Download\nexport=Pictures/App\tPictures/Real\n",
        );
        let plain = Rule::parse("enabled=1\nproviders=0\nlocation=2\nshared=com.c\tDocs\n");
        let off = Rule::parse("enabled=0\nproviders=1\n");
        let text = encode(&[
            PolicyEntry { uid: 10_123, package: "com.a", rule: &guarded },
            PolicyEntry { uid: 10_124, package: "com.b", rule: &plain },
            PolicyEntry { uid: 10_125, package: "com.d", rule: &off },
        ]);
        assert_eq!(
            text,
            "uid=10123\npkg=com.a\nproviders=1\ngallery=1\nroot=Android/data/com.a/sdcard\n\
             accessible=Download\nexport=Pictures/App\tPictures/Real\n\
             uid=10124\npkg=com.b\nproviders=0\ngallery=0\nroot=Android/data/com.b/cache/sdcard\n\
             shared=Docs\tAndroid/data/com.c/sdcard/Docs\n"
        );
    }
}
