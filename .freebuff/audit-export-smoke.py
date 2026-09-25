import json
import urllib.request
import urllib.error

BASE = "http://localhost:8080"


def login(email, password):
    req = urllib.request.Request(BASE + "/api/v1/auth/login", method="POST")
    req.add_header("Content-Type", "application/json")
    with urllib.request.urlopen(req, json.dumps(
            {"email": email, "password": password}).encode()) as r:
        return json.loads(r.read().decode())["data"]["token"]


def get(path, token=None):
    req = urllib.request.Request(BASE + path)
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req) as r:
            return r.status, dict(r.headers), r.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        return e.code, dict(e.headers), e.read().decode("utf-8", "replace")


admin = login("admin@hrgenius.local", "Admin@123")

status, headers, body = get("/api/v1/audit/export.csv", admin)
print("all-rows export:", status, "|", headers.get("Content-Type"))
print("  disposition:", headers.get("Content-Disposition"))
lines = body.rstrip("\r\n").split("\r\n")
print("  lines:", len(lines), "| first data row:", lines[1][:80] if len(lines) > 1 else "-")

# Feed total must equal CSV data rows (same filters, newest first).
status, _, feed = get("/api/v1/audit?page=0&size=1", admin)
total = json.loads(feed)["data"]["totalElements"]
print("feed total:", total, "| csv data rows:", len(lines) - 1, "| match:", total == len(lines) - 1)

status, _, body2 = get("/api/v1/audit/export.csv?action=LEAVE_TYPE_CREATED", admin)
print("action-filtered rows:", len(body2.rstrip("\r\n").split("\r\n")) - 1, "(expect 1)")

status, _, body3 = get("/api/v1/audit/export.csv?search=zzz-no-match", admin)
print("no-match export:", len(body3.rstrip("\r\n").split("\r\n")) - 1, "rows (expect 0)")

status, _, body4 = get("/api/v1/audit/export.csv?search=Smoke", admin)
print("search 'Smoke':", len(body4.rstrip("\r\n").split("\r\n")) - 1, "rows (expect 2)")

status, _, body5 = get("/api/v1/audit/export.csv?limit=1", admin)
print("limit=1 rows:", len(body5.rstrip("\r\n").split("\r\n")) - 1, "(expect 1)")

status, _, _ = get("/api/v1/audit/export.csv", login("manager@hrgenius.local", "Manager@123"))
print("manager export:", status, "(expect 403)")

status, _, _ = get("/api/v1/audit/export.csv")
print("anonymous export:", status, "(expect 401)")
