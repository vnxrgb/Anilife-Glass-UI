wit_bindgen::generate!({ path: "../wit/community", world: "provider" });
use anilili::provider::{host, types};
struct Fixture;
impl Guest for Fixture {
    fn info() -> types::ProviderInfo {
        types::ProviderInfo { name: "Sandbox fixture".into(), hosts: vec!["example.org".into()] }
    }
    fn servers(query: types::Query) -> Result<Vec<types::Server>, String> {
        let response = host::http(&types::Request { method: "GET".into(), url: "https://example.org/".into(), headers: vec![], body: None })?;
        if response.status != 200 { return Err("fixture HTTP failed".into()); }
        Ok(vec![types::Server { name: "Fixture".into(), dub: query.dub, headers: vec![], streams: vec![types::MediaStream { url: "https://example.org/fixture.mp4".into(), quality: Some("720p".into()), format: "mp4".into() }] }])
    }
}
export!(Fixture);
