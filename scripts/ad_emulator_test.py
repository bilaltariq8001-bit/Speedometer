#!/usr/bin/env python3
"""
Emulator smoke + timing test for the ad stack. Drives the real app with adb and reads Logcat.

Usage: ad_emulator_test.py <apk> <label> <out_dir>

Measures from the app's own debug analytics lines ("Analytics: event ads_loaded format=... placement=..."), which exist
in both the old and the new code, so the same numbers compare before and after. Writes <out_dir>/<label>.json and
screenshots.
"""
import json, os, re, subprocess, sys, time, xml.etree.ElementTree as ET

PKG = "com.speedometer.app"
APK, LABEL, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
os.makedirs(OUT, exist_ok=True)
result = {"label": LABEL, "phases": {}, "notes": []}


def sh(cmd, check=False, timeout=60):
    try:
        p = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=timeout)
    except subprocess.TimeoutExpired:
        result["notes"].append(f"timeout: {cmd}")
        subprocess.run("adb reconnect offline; adb wait-for-device", shell=True, timeout=120)
        return ""
    if check and p.returncode != 0:
        raise RuntimeError(f"{cmd}: {p.stderr}")
    return p.stdout


def adb(cmd, **kw):
    return sh(f"adb shell {cmd}", **kw)


def now_ms():
    return int(adb("date +%s%3N").strip() or 0)


def logcat_since(t0):
    """Lines (ms, text) since device time t0 from the main buffer."""
    raw = sh("adb logcat -d -v epoch -s Analytics:D AdPerf:D AndroidRuntime:E", timeout=60)
    out = []
    for line in raw.splitlines():
        m = re.match(r"\s*(\d+)\.(\d+)\s+\d+\s+\d+\s+\w\s+(\S+)\s*:\s*(.*)", line)
        if not m:
            continue
        ms = int(m.group(1)) * 1000 + int(m.group(2)[:3])
        if ms >= t0:
            out.append((ms, m.group(3), m.group(4)))
    return out


def events(lines, name, fmt=None):
    r = []
    for ms, tag, text in lines:
        if tag == "Analytics" and f"event {name} " in text and (fmt is None or f"format={fmt}" in text):
            r.append(ms)
    return r


def perf(lines, needle):
    return [ms for ms, tag, text in lines if tag == "AdPerf" and needle in text]


def crashes(lines):
    return [text for ms, tag, text in lines if tag == "AndroidRuntime" and ("FATAL" in text or PKG in text)]


def ui_nodes():
    adb("uiautomator dump /sdcard/ui.xml", timeout=30)
    xml = sh("adb exec-out cat /sdcard/ui.xml")
    try:
        root = ET.fromstring(xml)
    except ET.ParseError:
        return []
    return list(root.iter("node"))


def tap_text(*labels):
    for n in ui_nodes():
        t = (n.get("text") or "") + "|" + (n.get("content-desc") or "")
        if any(l.lower() == (n.get("text") or "").lower() or l.lower() == (n.get("content-desc") or "").lower() for l in labels):
            b = re.findall(r"\d+", n.get("bounds"))
            x = (int(b[0]) + int(b[2])) // 2
            y = (int(b[1]) + int(b[3])) // 2
            adb(f"input tap {x} {y}")
            return True
    return False


def screen_has(*labels):
    texts = [((n.get("text") or "") + " " + (n.get("content-desc") or "")).lower() for n in ui_nodes()]
    return any(any(l.lower() in t for t in texts) for l in labels)


def shot(name):
    sh(f"adb exec-out screencap -p > {OUT}/{LABEL}-{name}.png")


def launch():
    adb(f"monkey -p {PKG} -c android.intent.category.LAUNCHER 1")


def save():
    with open(f"{OUT}/{LABEL}.json", "w") as f:
        json.dump(result, f, indent=2)
    print("RESULT " + LABEL + " " + json.dumps(result), flush=True)


def first_after(ts, t0):
    later = [t for t in ts if t >= t0]
    return (later[0] - t0) if later else None


def dismiss_system_dialogs():
    for _ in range(3):
        if not tap_text("While using the app", "Allow", "Only this time", "OK", "Not now", "No thanks", "Got it"):
            break
        time.sleep(1)


# ------------------------------------------------------------------ install fresh
sh(f"adb uninstall {PKG}")
sh(f"adb install -r -g {APK}", check=True, timeout=240)
for perm in ["ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "POST_NOTIFICATIONS", "ACTIVITY_RECOGNITION"]:
    adb(f"pm grant {PKG} android.permission.{perm}")
sh("adb logcat -c")

# ------------------------------------------------------------------ 1-2. fresh install + onboarding
t0 = now_ms()
launch()
time.sleep(6)
shot("01-fresh-launch")
steps = []
for _ in range(25):
    if screen_has("Insights") and screen_has("Trips"):
        break
    for label in ["Continue", "Skip", "Get started", "Finish setup", "Next"]:
        if tap_text(label):
            steps.append(label)
            break
    else:
        dismiss_system_dialogs()
    time.sleep(2)
t_home = now_ms()
lines = logcat_since(t0)
onb = [l for l in lines if l[0] < t_home]
result["phases"]["onboarding"] = {
    "taps": steps,
    "reached_home": screen_has("Insights"),
    "ad_loads_during_onboarding": len(events(onb, "ads_loaded")),
    "ad_shows_during_onboarding": len(events(onb, "ads_shown")),
    "banner_requests_during_onboarding": len(perf(onb, "banner: request start")),
}
save()
shot("02-onboarding-done")

# ------------------------------------------------------------------ 3. first Home after onboarding
time.sleep(15)
lines = logcat_since(t_home)
result["phases"]["first_home_after_onboarding"] = {
    "banner_loaded_ms": first_after(events(lines, "ads_loaded", "banner"), t_home),
    "app_open_shown": len(events(lines, "ads_shown", "app_open")),
    "app_open_preloaded_ms": first_after(events(lines, "ads_loaded", "app_open"), t_home),
}
save()
shot("03-home")
dismiss_system_dialogs()

# ------------------------------------------------------------------ 7/11. navigation + recomposition
t_nav = now_ms()
navs = 0
for _ in range(5):
    if tap_text("Insights"):
        navs += 1
        time.sleep(2)
        adb("input keyevent KEYCODE_BACK")
        time.sleep(2)
lines = logcat_since(t_nav)
result["phases"]["navigation"] = {
    "round_trips": navs,
    "banner_loads": len(events(lines, "ads_loaded", "banner")),
    "banner_requests": len(perf(lines, "banner: request start")),
    "duplicate_loads_prevented": len(perf(lines, "duplicate load prevented")),
}
save()
shot("04-after-navigation")

# ------------------------------------------------------------------ 5. back from background
t_bg = now_ms()
adb("input keyevent KEYCODE_HOME")
time.sleep(4)
t_fg = now_ms()
launch()
time.sleep(6)
shot("05-foreground")
lines = logcat_since(t_bg)
result["phases"]["return_from_background"] = {
    "app_open_shown_after_ms": first_after(events(lines, "ads_shown", "app_open"), t_fg),
    "app_open_shows": len(events(lines, "ads_shown", "app_open")),
}
save()
adb("input keyevent KEYCODE_BACK")  # close the ad if it is up
time.sleep(2)

# ------------------------------------------------------------------ 6. quick minimise / reopen x3
t_q = now_ms()
for _ in range(3):
    adb("input keyevent KEYCODE_HOME"); time.sleep(0.7); launch(); time.sleep(1.2)
time.sleep(4)
lines = logcat_since(t_q)
result["phases"]["quick_minimise_reopen_x3"] = {
    "app_open_shows": len(events(lines, "ads_shown", "app_open")),
    "app_open_loads": len(events(lines, "ads_loaded", "app_open")),
}
save()
adb("input keyevent KEYCODE_BACK"); time.sleep(2)

# ------------------------------------------------------------------ 4. normal cold launch
adb(f"am force-stop {PKG}")
time.sleep(2)
t_cold = now_ms()
launch()
time.sleep(12)
shot("06-cold-launch")
lines = logcat_since(t_cold)
result["phases"]["cold_launch"] = {
    "banner_loaded_ms": first_after(events(lines, "ads_loaded", "banner"), t_cold),
    "app_open_loaded_ms": first_after(events(lines, "ads_loaded", "app_open"), t_cold),
    "app_open_shown_ms": first_after(events(lines, "ads_shown", "app_open"), t_cold),
    "sdk_ready": [t for _, tag, t in lines if tag == "AdPerf" and "MobileAds ready" in t][:1],
}
save()
adb("input keyevent KEYCODE_BACK"); time.sleep(2)

# ------------------------------------------------------------------ 12. rotation
t_rot = now_ms()
adb("settings put system accelerometer_rotation 0")
adb("settings put system user_rotation 1"); time.sleep(5)
shot("07-landscape")
adb("settings put system user_rotation 0"); time.sleep(5)
lines = logcat_since(t_rot)
result["phases"]["rotation"] = {"banner_requests": len(perf(lines, "banner: request start")), "crashes": crashes(lines)}

# ------------------------------------------------------------------ 8. slow network cold launch
sh("adb emu network speed gsm"); sh("adb emu network delay gprs")
adb(f"am force-stop {PKG}"); time.sleep(2)
t_slow = now_ms()
launch(); time.sleep(25)
shot("08-slow-network")
lines = logcat_since(t_slow)
result["phases"]["slow_network_cold_launch"] = {
    "banner_loaded_ms": first_after(events(lines, "ads_loaded", "banner"), t_slow),
    "app_open_shown_ms": first_after(events(lines, "ads_shown", "app_open"), t_slow),
    "home_visible": screen_has("Insights"),
}
save()
sh("adb emu network speed full"); sh("adb emu network delay none")
adb("input keyevent KEYCODE_BACK"); time.sleep(2)

# ------------------------------------------------------------------ 9-10. offline cold launch, then back online
adb("svc wifi disable"); adb("svc data disable"); time.sleep(3)
adb(f"am force-stop {PKG}"); time.sleep(2)
t_off = now_ms()
launch(); time.sleep(8)
shot("09-offline")
home_offline = screen_has("Insights")
adb("svc wifi enable"); adb("svc data enable")
t_on = now_ms()
time.sleep(20)
shot("10-back-online")
lines = logcat_since(t_off)
result["phases"]["offline_then_online"] = {
    "home_visible_offline": home_offline,
    "ads_loaded_while_offline": len([t for t in events(lines, "ads_loaded") if t < t_on]),
    "banner_loaded_after_network_back_ms": first_after(events(lines, "ads_loaded", "banner"), t_on),
    "load_failures": len(events(lines, "ads_failed")),
}
save()

# ------------------------------------------------------------------ crashes over the whole run
all_lines = logcat_since(t0)
result["crashes"] = crashes(all_lines)
result["process_alive"] = bool(adb(f"pidof {PKG}").strip())
sh(f"adb logcat -d -v epoch -s Analytics:D AdPerf:D AndroidRuntime:E > {OUT}/{LABEL}-logcat.txt")
with open(f"{OUT}/{LABEL}.json", "w") as f:
    json.dump(result, f, indent=2)
print(json.dumps(result, indent=2))
