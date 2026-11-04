"""Агент стенда: старый Nothing Phone (2) на ПК Адониса для тренировок девчонок с EDL.

Сам опрашивает сервер (наружу открывать ничего не нужно), берёт команду, выполняет ТОЛЬКО разрешённое
и отправляет вывод обратно. Этап 1 — только чтение: usb, printgpt, getstorageinfo. Записи нет.
Остановить доступ: закрыть это окно (Ctrl+C).

Запуск:  py stand_agent.py        (настройки — в stand_agent.json рядом, его создаст первый запуск)
"""
import json, os, re, subprocess, sys, time, urllib.error, urllib.parse, urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
CFG = HERE / "stand_agent.json"
WORK = HERE / "стенд"                     # папка стенда: здесь edl пишет свои файлы
STAGE1 = ["usb", "printgpt", "getstorageinfo"]
ANSI = re.compile(r"\x1b\[[0-9;]*[A-Za-z]")
DEFAULT = {"server": "https://avrora-exe.ru/app-api", "token": "", "edl": "edl", "loader": "", "allow": STAGE1}


def load_cfg() -> dict:
    if not CFG.exists():
        CFG.write_text(json.dumps(DEFAULT, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"Создал {CFG.name}. Впиши туда token (STAND_TOKEN с сервера) и запусти снова.")
        sys.exit(1)
    c = {**DEFAULT, **json.loads(CFG.read_text(encoding="utf-8"))}
    if not c["token"]:
        print(f"В {CFG.name} пустой token."); sys.exit(1)
    # разрешено только то, что есть и в настройках, и в списке этапа 1
    c["allow"] = [v for v in c["allow"] if v in STAGE1]
    return c


def http(c: dict, method: str, path: str, body: dict | None = None, timeout: float = 60) -> dict:
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(c["server"].rstrip("/") + path, data=data, method=method,
                                 headers={"Authorization": f"Bearer {c['token']}", "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return json.loads(r.read().decode())


def list_usb() -> tuple[int, str]:
    try:
        import usb.core
    except ImportError:
        return 1, "нет pyusb: py -m pip install pyusb"
    try:
        devs = list(usb.core.find(find_all=True))
    except Exception as e:   # нет libusb-драйвера (Zadig) и т.п.
        return 1, f"USB не читается: {type(e).__name__}: {e}"
    lines = []
    for d in devs:
        mark = "  ← телефон в EDL (Qualcomm 9008)" if (d.idVendor, d.idProduct) == (0x05C6, 0x9008) else ""
        lines.append(f"{d.idVendor:04x}:{d.idProduct:04x}{mark}")
    return 0, "\n".join(lines) or "USB-устройств не видно"


def run_edl(c: dict, verb: str) -> tuple[int, str]:
    argv = [c["edl"], verb]
    if c.get("loader"):
        argv.append(f"--loader={c['loader']}")
    WORK.mkdir(exist_ok=True)
    try:
        p = subprocess.run(argv, cwd=WORK, capture_output=True, timeout=90)
    except FileNotFoundError:
        return 1, f"не найдена программа edl ({c['edl']}): запусти install.bat"
    except subprocess.TimeoutExpired:
        return 1, "edl не уложился в 90 с (телефон не отвечает?)"
    out = (p.stdout + p.stderr).decode("utf-8", "replace")
    return p.returncode, ANSI.sub("", out)[-6000:]


def main() -> None:
    c = load_cfg()
    print(f"Стенд на связи с {c['server']}. Разрешено: {', '.join(c['allow'])}. Остановить: Ctrl+C.")
    q = urllib.parse.urlencode({"wait": 25, "agent": os.environ.get("COMPUTERNAME", "pc"), "verbs": ",".join(c["allow"])})
    while True:
        try:
            job = http(c, "GET", f"/stand/next?{q}", timeout=40).get("job")
        except urllib.error.HTTPError as e:
            print(f"сервер ответил {e.code}" + (" — проверь token" if e.code == 401 else "")); time.sleep(15); continue
        except Exception as e:
            print(f"нет связи: {type(e).__name__}"); time.sleep(10); continue
        if not job:
            continue
        verb = str(job.get("verb", ""))
        print(time.strftime("%H:%M:%S"), "команда:", verb)
        if verb not in c["allow"]:
            code, out = 1, f"на этом ПК команда «{verb}» не разрешена"
        elif verb == "usb":
            code, out = list_usb()
        else:
            code, out = run_edl(c, verb)
        print(out[-400:])
        try:
            http(c, "POST", "/stand/result", {"id": job["id"], "code": code, "out": out})
        except Exception as e:
            print(f"ответ не ушёл: {type(e).__name__}")


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\nСтенд остановлен.")
