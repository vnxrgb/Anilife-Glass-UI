use anilumos_wasm_runtime::adapter::*;
struct FixtureBroker;
impl HttpBroker for FixtureBroker {
    fn browser_fetch(&mut self, origin: String, request: HttpRequest) -> Result<HttpReply, String> {
        assert_eq!(origin, "https://example.org/fixture");
        self.fetch(request)
    }
    fn fetch(&mut self, request: HttpRequest) -> Result<HttpReply, String> {
        assert_eq!(request.url, "https://example.org/");
        assert_eq!(request.method, "GET");
        Ok(HttpReply { status: 200, headers: vec![], body: b"fixture".to_vec() })
    }
}
#[test]
fn component_calls_host_and_returns_stream() {
    let Some(path) = std::env::var_os("WASM_FIXTURE") else {
        assert!(std::env::var_os("CI").is_none(), "CI must supply fixture");
        return;
    };
    let bytes = std::fs::read(path).unwrap();
    let metadata = inspect(&bytes, true).unwrap();
    assert_eq!(metadata.name, "Sandbox fixture");
    let servers = resolve(&bytes, true, EpisodeQuery { anilist_id: 1, mal_id: None, titles: vec!["Fixture".into()], episode: 1.0, dub: true }, Box::new(FixtureBroker)).unwrap();
    assert_eq!(servers.len(), 1);
    assert!(servers[0].dub);
    assert_eq!(servers[0].streams[0].url, "https://example.org/fixture.mp4");
}
