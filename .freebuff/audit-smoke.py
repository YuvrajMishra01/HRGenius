import json
import urllib.request

BASE = "http://localhost:8080"


def call(method, path, token=None, body=None):
    req = urllib.request.Request(BASE + path, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    data = json.dumps(body).encode() if body is not None else None
    try:
        with urllib.request.urlopen(req, data) as r:
            raw = r.read().decode()
            return r.status, (json.loads(raw) if raw.strip() else {})
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read().decode())


def login(email, password):
    status, res = call("POST", "/api/v1/auth/login",
                       body={"email": email, "password": password})
    assert status == 200, "login failed: %s %s" % (status, res)
    return res["data"]["token"]


hr = login("hr@hrgenius.local", "Hr@12345")

# clean up any leftover "Smoke Check" type from an earlier failed run
status, res = call("GET", "/api/v1/leave/types", hr)
for t in res.get("data", []):
    if t.get("name") == "Smoke Check":
        call("DELETE", "/api/v1/leave/types/%s" % t["id"], hr)
        print("removed leftover smoke type id", t["id"])

status, res = call("POST", "/api/v1/leave/types", hr, {
    "name": "Smoke Check", "yearlyLimit": 1,
    "description": "post-restart audit smoke test"})
print("create leave-type:", status)
lt_id = res["data"]["id"]

status, _ = call("DELETE", "/api/v1/leave/types/%s" % lt_id, hr)
print("delete leave-type:", status)

admin = login("admin@hrgenius.local", "Admin@123")
status, res = call("GET", "/api/v1/audit?page=0&size=5", admin)
assert status == 200, "audit read failed: %s" % status
page = res["data"]
print("audit total:", page["totalElements"])
for e in page["content"][:5]:
    print(" -", e["action"], "|", e["actorName"], "|", e.get("entityLabel"))
