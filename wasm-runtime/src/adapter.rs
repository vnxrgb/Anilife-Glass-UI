//! Typed ABI bindings for the two structurally distinct provider worlds.
//! All host operations deny by default until the authenticated IPC broker is wired.
use anyhow::Result;
use wasmtime::{Engine, Store, StoreLimitsBuilder};
use wasmtime::component::{Component, Linker};

mod modern {
    wasmtime::component::bindgen!({ path: "wit/desktop", world: "provider" });
}
mod legacy {
    wasmtime::component::bindgen!({ path: "wit/community", world: "provider" });
}

#[derive(Debug, Clone, PartialEq)]
pub struct ProviderMetadata {
    pub name: String,
    pub id: Option<String>,
    pub version: Option<String>,
    pub hosts: Vec<String>,
}

// Standard WASI HTTP types are available, but all direct requests fail closed.
// Provider network access remains exclusively through the authenticated custom broker.
struct DenyDirectHttp;
impl wasmtime_wasi_http::WasiHttpHooks for DenyDirectHttp {
    // Reject every scheme before building a direct HTTP request. The explicit
    // send_request denial below remains a second independent safety boundary.
    fn is_supported_scheme(&mut self, _: &http::uri::Scheme) -> bool { false }
    fn default_scheme(&mut self) -> Option<http::uri::Scheme> { None }

    fn send_request(
        &mut self,
        _request: http::Request<wasmtime_wasi_http::WasiBody>,
        _options: Option<wasmtime_wasi_http::RequestOptions>,
        _completion: Box<dyn std::future::Future<Output = wasmtime_wasi_http::Result<()>> + Send>,
    ) -> Box<dyn std::future::Future<Output = wasmtime_wasi_http::Result<(
        http::Response<wasmtime_wasi_http::WasiBody>,
        Box<dyn std::future::Future<Output = wasmtime_wasi_http::Result<()>> + Send>,
    )>> + Send> {
        Box::new(async { Err(wasmtime_wasi_http::Error::HttpRequestDenied) })
    }
}

struct DeniedHost {
    broker: Option<Box<dyn HttpBroker + Send>>,
    cache: std::collections::HashMap<String, (String, std::time::Instant)>,
    limits: wasmtime::StoreLimits,
    wasi: wasmtime_wasi::WasiCtx,
    http: wasmtime_wasi_http::WasiHttpCtx,
    http_hooks: DenyDirectHttp,
    table: wasmtime::component::ResourceTable,
}

impl DeniedHost {
    fn cached(&mut self, key: &str) -> Option<String> {
        let now = std::time::Instant::now();
        self.cache.retain(|_, (_, expiry)| *expiry > now);
        self.cache.get(key).map(|(value, _)| value.clone())
    }
    fn cache_value(&mut self, key: String, value: String, ttl: u32) {
        if self.broker.is_none() || key.len() > 512 || value.len() > 32768 || ttl == 0 { return; }
        let now = std::time::Instant::now();
        self.cache.retain(|_, (_, expiry)| *expiry > now);
        if self.cache.len() >= 32 && !self.cache.contains_key(&key) { return; }
        self.cache.insert(key, (value, now + std::time::Duration::from_secs(ttl.min(300) as u64)));
    }
}

impl wasmtime_wasi::WasiView for DeniedHost {
    fn ctx(&mut self) -> wasmtime_wasi::WasiCtxView<'_> {
        wasmtime_wasi::WasiCtxView { ctx: &mut self.wasi, table: &mut self.table }
    }
}

impl wasmtime_wasi_http::WasiHttpView for DeniedHost {
    fn http(&mut self) -> wasmtime_wasi_http::WasiHttpCtxView<'_> {
        wasmtime_wasi_http::WasiHttpCtxView {
            ctx: &mut self.http, table: &mut self.table, hooks: &mut self.http_hooks,
        }
    }
}

impl modern::anilili::provider::types::Host for DeniedHost {}
impl modern::anilili::provider::host::Host for DeniedHost {
    fn http(&mut self, req: modern::anilili::provider::host::Request) -> std::result::Result<modern::anilili::provider::host::Response, String> {
        let broker = self.broker.as_mut().ok_or("network broker unavailable")?;
        let r = broker.fetch(HttpRequest { method: req.method, url: req.url, headers: req.headers, body: req.body })?;
        Ok(modern::anilili::provider::host::Response { status: r.status, headers: r.headers, body: r.body })
    }
    fn browser_fetch(&mut self, origin: String, req: modern::anilili::provider::host::Request) -> std::result::Result<modern::anilili::provider::host::Response, String> {
        let broker = self.broker.as_mut().ok_or("network broker unavailable")?;
        let r = broker.browser_fetch(origin, HttpRequest { method: req.method, url: req.url, headers: req.headers, body: req.body })?;
        Ok(modern::anilili::provider::host::Response { status: r.status, headers: r.headers, body: r.body })
    }
    fn log(&mut self, _: String) {}
    fn cache_get(&mut self, key: String) -> Option<String> { self.cached(&key) }
    fn cache_set(&mut self, key: String, value: String, ttl: u32) { self.cache_value(key, value, ttl); }
}

impl legacy::anilili::provider::types::Host for DeniedHost {}
impl legacy::anilili::provider::host::Host for DeniedHost {
    fn http(&mut self, req: legacy::anilili::provider::types::Request) -> std::result::Result<legacy::anilili::provider::types::Response, String> {
        let broker = self.broker.as_mut().ok_or("network broker unavailable")?;
        let r = broker.fetch(HttpRequest { method: req.method, url: req.url, headers: req.headers, body: req.body })?;
        Ok(legacy::anilili::provider::types::Response { status: r.status, headers: r.headers, body: r.body })
    }
    fn browser_fetch(&mut self, origin: String, req: legacy::anilili::provider::types::Request) -> std::result::Result<legacy::anilili::provider::types::Response, String> {
        let broker = self.broker.as_mut().ok_or("network broker unavailable")?;
        let r = broker.browser_fetch(origin, HttpRequest { method: req.method, url: req.url, headers: req.headers, body: req.body })?;
        Ok(legacy::anilili::provider::types::Response { status: r.status, headers: r.headers, body: r.body })
    }
    fn log(&mut self, _: String) {}
    fn cache_get(&mut self, key: String) -> Option<String> { self.cached(&key) }
    fn cache_set(&mut self, key: String, value: String, ttl: u32) { self.cache_value(key, value, ttl); }
}

fn store(engine: &Engine) -> Result<Store<DeniedHost>> {
    let mut store = Store::new(engine, DeniedHost {
        broker: None,
        cache: std::collections::HashMap::new(),
        wasi: wasmtime_wasi::WasiCtx::builder().build(),
        http: wasmtime_wasi_http::WasiHttpCtx::new(),
        http_hooks: DenyDirectHttp,
        table: wasmtime::component::ResourceTable::new(),
        limits: StoreLimitsBuilder::new().memory_size(64 << 20).instances(32).memories(4).tables(16).build(),
    });
    store.limiter(|s| &mut s.limits);
    store.set_fuel(100_000_000)?;
    Ok(store)
}

/// The caller chooses the ABI from reviewed metadata, never by executing unknown variants.
pub fn inspect(bytes: &[u8], community: bool) -> Result<ProviderMetadata> {
    anyhow::ensure!(bytes.len() <= 64 << 20 && bytes.starts_with(b"\0asm\x0d\0\x01\0"), "invalid component");
    let engine = super::engine()?;
    let component = Component::new(&engine, bytes)?;
    let mut store = store(&engine)?;
    let mut linker = Linker::new(&engine);
    wasmtime_wasi::p2::add_to_linker_sync(&mut linker)?;
    wasmtime_wasi_http::p2::add_only_http_to_linker_sync(&mut linker)?;
    let result = if community {
        legacy::Provider::add_to_linker::<_, wasmtime::component::HasSelf<_>>(&mut linker, |s| s)?;
        let instance = legacy::Provider::instantiate(&mut store, &component, &linker)?;
        let info = instance.call_info(&mut store)?;
        ProviderMetadata { name: info.name, id: None, version: None, hosts: info.hosts }
    } else {
        modern::Provider::add_to_linker::<_, wasmtime::component::HasSelf<_>>(&mut linker, |s| s)?;
        let instance = modern::Provider::instantiate(&mut store, &component, &linker)?;
        let info = instance.call_info(&mut store)?;
        ProviderMetadata { name: info.name, id: Some(info.id), version: Some(info.version), hosts: info.hosts }
    };
    anyhow::ensure!(result.name.len() <= 128 && result.hosts.len() <= 64 && result.hosts.iter().all(|h| h.len() <= 253), "oversized metadata");
    Ok(result)
}

#[derive(serde::Serialize)]
pub struct HttpRequest {
    pub method: String,
    pub url: String,
    pub headers: Vec<(String, String)>,
    pub body: Option<Vec<u8>>,
}
#[derive(serde::Deserialize)]
pub struct HttpReply {
    pub status: u16,
    pub headers: Vec<(String, String)>,
    pub body: Vec<u8>,
}
/// A trusted external implementation must enforce all destination, credential and time limits.
pub trait HttpBroker {
    fn fetch(&mut self, request: HttpRequest) -> std::result::Result<HttpReply, String>;
    fn browser_fetch(&mut self, _origin: String, _request: HttpRequest) -> std::result::Result<HttpReply, String> { Err("browser capability unavailable".into()) }
}

#[derive(serde::Deserialize)]
pub struct EpisodeQuery {
    pub anilist_id: u32,
    pub mal_id: Option<u32>,
    pub titles: Vec<String>,
    pub episode: f64,
    pub dub: bool,
}
#[derive(serde::Serialize)]
pub struct Stream {
    pub url: String,
    pub format: String,
    pub quality: Option<String>,
    pub height: Option<u32>,
}
#[derive(serde::Serialize)]
pub struct Subtitle {
    pub url: String,
    pub label: String,
    pub language: String,
}
#[derive(serde::Serialize)]
pub struct Server {
    pub name: String,
    pub dub: bool,
    pub headers: Vec<(String, String)>,
    pub streams: Vec<Stream>,
    pub subtitles: Vec<Subtitle>,
    pub skip_intro: Option<(f64, f64)>,
    pub skip_outro: Option<(f64, f64)>,
    pub thumbnails: Option<String>,
}
/// Executes one reviewed component with an explicit per-invocation broker.
/// Results are untrusted and must be checked by the Android host before playback.
pub fn resolve(bytes: &[u8], community: bool, query: EpisodeQuery, broker: Box<dyn HttpBroker + Send>) -> Result<Vec<Server>> {
    anyhow::ensure!(bytes.len() <= 64 << 20 && bytes.starts_with(b"\0asm\x0d\0\x01\0"), "invalid component");
    anyhow::ensure!(query.episode.is_finite() && query.episode > 0.0 && query.titles.len() <= 32 && query.titles.iter().all(|t| t.len() <= 512), "invalid query");
    let engine = super::engine()?;
    let component = Component::new(&engine, bytes)?;
    let mut store = store(&engine)?;
    let mut linker = Linker::new(&engine);
    wasmtime_wasi::p2::add_to_linker_sync(&mut linker)?;
    wasmtime_wasi_http::p2::add_only_http_to_linker_sync(&mut linker)?;
    let result: Vec<Server> = if community {
        legacy::Provider::add_to_linker::<_, wasmtime::component::HasSelf<_>>(&mut linker, |s| s)?;
        let instance = legacy::Provider::instantiate(&mut store, &component, &linker)?;
        store.data_mut().broker = Some(broker);
        let title = legacy::anilili::provider::types::Query { anilist_id: query.anilist_id, mal_id: query.mal_id, titles: query.titles, episode: query.episode, dub: query.dub };
        instance.call_servers(&mut store, &title)?.map_err(|e| anyhow::anyhow!(e))?.into_iter().take(32).map(|s| Server {
            name: s.name, dub: s.dub, headers: s.headers,
            streams: s.streams.into_iter().take(32).map(|st| Stream { url: st.url, format: st.format, quality: st.quality, height: None }).collect(),
            subtitles: vec![], skip_intro: None, skip_outro: None, thumbnails: None,
        }).collect()
    } else {
        modern::Provider::add_to_linker::<_, wasmtime::component::HasSelf<_>>(&mut linker, |s| s)?;
        let instance = modern::Provider::instantiate(&mut store, &component, &linker)?;
        store.data_mut().broker = Some(broker);
        let title = modern::anilili::provider::types::Title { anilist_id: query.anilist_id, mal_id: query.mal_id, titles: query.titles, episode: query.episode, dub: query.dub };
        instance.call_servers(&mut store, &title)?.map_err(|e| anyhow::anyhow!(e))?.into_iter().take(32).map(|s| Server {
            name: s.name, dub: s.dub, headers: s.headers,
            streams: s.streams.into_iter().take(32).map(|st| Stream { url: st.url, format: st.format, quality: st.quality, height: st.height }).collect(),
            subtitles: s.subtitles.into_iter().take(32).map(|st| Subtitle { url: st.url, label: st.label, language: st.language }).collect(),
            skip_intro: s.skip_intro, skip_outro: s.skip_outro, thumbnails: s.thumbnails,
        }).collect()
    };
    Ok(result)
}

/** Bounded response framing: BE metadata length, UTF-8 JSON metadata, raw body. */
pub fn decode_broker_reply(bytes: &[u8]) -> std::result::Result<HttpReply, String> {
    if bytes.len() < 4 || bytes.len() > (4 << 20) + (64 << 10) + 4 { return Err("invalid broker reply".into()); }
    let n = u32::from_be_bytes(bytes[..4].try_into().unwrap()) as usize;
    if n == 0 || n > (64 << 10) || n > bytes.len() - 4 || bytes.len() - 4 - n > (4 << 20) { return Err("invalid reply length".into()); }
    #[derive(serde::Deserialize)]
    struct Metadata { status: u16, headers: Vec<(String, String)> }
    let metadata: Metadata = serde_json::from_slice(&bytes[4..4+n]).map_err(|e| e.to_string())?;
    Ok(HttpReply { status: metadata.status, headers: metadata.headers, body: bytes[4+n..].to_vec() })
}
#[cfg(test)]
mod framing_tests {
    use super::*;
    #[test]
    fn binary_body_roundtrip_and_invalid_length() {
        let metadata = br#"{"status":200,"headers":[]}"#;
        let mut bytes = (metadata.len() as u32).to_be_bytes().to_vec();
        bytes.extend_from_slice(metadata);
        bytes.extend_from_slice(&vec![42; 256 * 1024]);
        let reply = decode_broker_reply(&bytes).unwrap();
        assert_eq!(reply.status, 200);
        assert_eq!(reply.body, vec![42; 256 * 1024]);
        assert!(decode_broker_reply(&[0, 0, 0, 10, 0]).is_err());
        assert!(decode_broker_reply(&[]).is_err());
    }
}

#[cfg(test)]
mod direct_http_policy_tests {
    use super::DenyDirectHttp;
    use wasmtime_wasi_http::WasiHttpHooks;
    #[test]
    fn direct_http_rejects_all_schemes_and_has_no_default() {
        let mut hooks = DenyDirectHttp;
        assert!(!hooks.is_supported_scheme(&http::uri::Scheme::HTTP));
        assert!(!hooks.is_supported_scheme(&http::uri::Scheme::HTTPS));
        assert!(hooks.default_scheme().is_none());
    }
}
