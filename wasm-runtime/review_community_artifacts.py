"""Fetch pinned candidate archives and verify exact bytes without executing providers."""
import hashlib
import json
import pathlib
import subprocess
import sys
import zipfile

manifest = json.loads(pathlib.Path("wasm-runtime/community-candidates.json").read_text())
out = pathlib.Path(sys.argv[1])
out.mkdir(parents=True, exist_ok=True)
for entry in manifest["candidates"]:
    archive = out / (entry["id"] + ".zip")
    with archive.open("wb") as stream:
        subprocess.run(["gh", "api", f"repos/{manifest['repository']}/actions/artifacts/{entry['artifact_id']}/zip"], stdout=stream, check=True)
    assert archive.stat().st_size <= 16 * 1024 * 1024
    assert hashlib.sha256(archive.read_bytes()).hexdigest() == entry["archive_sha256"]
    with zipfile.ZipFile(archive) as z:
        member = z.getinfo(entry["filename"])
        assert member.file_size == entry["size"] <= 32 * 1024 * 1024
        module = z.read(member)
    assert module[:8] == bytes.fromhex("0061736d0d000100")
    assert hashlib.sha256(module).hexdigest() == entry["module_sha256"]
    (out / (entry["id"] + ".wasm")).write_bytes(module)
    archive.unlink()
    print(f"{entry['id']}: verified {len(module)} bytes")
