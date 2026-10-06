use crate::consts;

pub enum Location {
    Data,
    Cache,
}

impl Location {
    pub fn isolated_relative_root(&self, package: &str) -> String {
        match self {
            Location::Data => format!("Android/data/{package}/sdcard"),
            Location::Cache => format!("Android/data/{package}/cache/sdcard"),
        }
    }
}

pub struct ExportRule {
    pub view: String,
    pub real: String,
}

pub struct SharedFolder {
    pub package: String,
    pub path: String,
}

pub struct Rule {
    pub enabled: bool,
    pub location: Location,
    pub gallery: bool,
    pub providers: bool,
    pub accessible: Vec<String>,
    pub exports: Vec<ExportRule>,
    pub shared: Vec<SharedFolder>,
}

impl Rule {
    pub fn parse(text: &str) -> Rule {
        let mut rule = Rule {
            enabled: false,
            location: Location::Data,
            gallery: false,
            providers: false,
            accessible: Vec::new(),
            exports: Vec::new(),
            shared: Vec::new(),
        };
        for line in text.lines() {
            let Some((key, value)) = line.trim_end_matches('\r').split_once('=') else {
                continue;
            };
            match key {
                "enabled" => rule.enabled = value == "1",
                "location" => {
                    rule.location = if value == consts::LOCATION_CACHE {
                        Location::Cache
                    } else {
                        Location::Data
                    }
                }
                "gallery" => rule.gallery = value == "1",
                "providers" => rule.providers = value == "1",
                "accessible" => {
                    if let Some(path) = normalize_relative(value) {
                        rule.accessible.push(path);
                    }
                }
                "export" => {
                    if let Some((view, real)) = value.split_once('\t') {
                        if let (Some(view), Some(real)) =
                            (normalize_relative(view), normalize_relative(real))
                        {
                            rule.exports.push(ExportRule { view, real });
                        }
                    }
                }
                "shared" => {
                    if let Some((package, path)) = value.split_once('\t') {
                        if let (true, Some(path)) =
                            (is_valid_package(package), normalize_relative(path))
                        {
                            rule.shared.push(SharedFolder { package: String::from(package), path });
                        }
                    }
                }
                _ => {}
            }
        }
        rule
    }

    pub fn load_enabled(user_id: u32, package: &str) -> Option<Rule> {
        if !is_valid_package(package) {
            return None;
        }
        let text = std::fs::read_to_string(rule_path(user_id, package)).ok()?;
        let rule = Rule::parse(&text);
        rule.enabled.then_some(rule)
    }
}

pub fn isolated_root_of(user_id: u32, package: &str) -> String {
    let location = std::fs::read_to_string(rule_path(user_id, package))
        .map(|text| Rule::parse(&text).location)
        .unwrap_or(Location::Data);
    location.isolated_relative_root(package)
}

pub fn rule_path(user_id: u32, package: &str) -> String {
    format!("{}/{}/{}{}", consts::RULES_DIR, user_id, package, consts::RULE_FILE_SUFFIX)
}

pub fn normalize_relative(path: &str) -> Option<String> {
    if path.chars().any(|c| matches!(c, '\t' | '\n' | '\r' | '\0')) {
        return None;
    }
    let segments: Vec<&str> = path.trim().split('/').filter(|s| !s.is_empty()).collect();
    if segments.is_empty() || segments.iter().any(|s| *s == "." || *s == "..") {
        return None;
    }
    Some(segments.join("/"))
}

pub fn is_valid_package(name: &str) -> bool {
    !name.is_empty()
        && name.len() <= 255
        && name.bytes().all(|b| b.is_ascii_alphanumeric() || b == b'.' || b == b'_')
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_every_key() {
        let rule = Rule::parse(
            "v=1\nenabled=1\nlocation=2\ngallery=1\nproviders=0\naccessible=/Download//\n\
             accessible=../etc\nexport=Pictures/App\tPictures/Real\nshared=com.a.b\tDocs\n",
        );
        assert!(rule.enabled);
        assert!(matches!(rule.location, Location::Cache));
        assert!(rule.gallery);
        assert_eq!(rule.accessible, vec![String::from("Download")]);
        assert_eq!(rule.exports.len(), 1);
        assert_eq!(rule.exports[0].real, "Pictures/Real");
        assert_eq!(rule.shared[0].package, "com.a.b");
    }

    #[test]
    fn rejects_escaping_paths() {
        assert!(normalize_relative("a/../b").is_none());
        assert!(normalize_relative("  ").is_none());
        assert_eq!(normalize_relative("/a/b/").as_deref(), Some("a/b"));
    }
}
