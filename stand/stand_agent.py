"""Агент стенда: старый Nothing Phone (2) на ПК Адониса — стенд для тренировок девчонок (EDL, fastboot, adb).

Сам опрашивает сервер (наружу открывать ничего не нужно), берёт команду, выполняет и отправляет вывод обратно.
С ТЕЛЕФОНОМ можно всё: edl (чтение, запись, стирание), fastboot, adb, скачивание прошивок в папку стенда.
С ПК — ничего сверх этого: запускаются только edl / fastboot / adb, без командной оболочки, а все файлы — только
внутри папки «стенд» рядом с агентом. Остановить доступ: закрыть окно (Ctrl+C).

Запуск:  start.bat  (или py stand_agent.py; настройки — stand_agent.json рядом; его создаст первый запуск)
"""
import json, os, re, shlex, subprocess, sys, time, urllib.error, urllib.parse, urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
CFG = HERE / "stand_agent.json"
WORK = HERE / "стенд"
PT = HERE / "platform-tools"
ANSI = re.compile(r"\x1b\[[0-9;]*[A-Za-z]")
ALL_TOOLS = ["usb", "edl", "fastboot", "adb", "файлы", "скачать"]
EXE = ".exe" if os.name == "nt" else ""
DEFAULT = {
    "server": "https://avrora-exe.ru/app-api", "token": "",
    "edl": "edl", "fastboot": str(PT / ("fastboot" + EXE)), "adb": str(PT / ("adb" + EXE)),
    "tools": ALL_TOOLS, "timeout_min": 30, "download_max_mb": 4096,
}
# adb, которые открывают сеть или туннели на ПК, — не для стенда
ADB_BLOCKED = {"connect", "disconnect", "pair", "forward", "reverse", "tcpip", "ppp", "start-server", "kill-server", "server", "nodaemon"}
EDL_BLOCKED = {"server"}   # «edl server» открывает порт на ПК


def load_cfg() -> dict:
    if not CFG.exists():
        CFG.write_text(json.dumps(DEFAULT, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"Создал {CFG.name}. Впиши туда token (токен стенда с сервера) и запусти снова.")
        sys.exit(1)
    c = {**DEFAULT, **json.loads(CFG.read_text(encoding="utf-8"))}
    if not c["token"]:
        print(f"В {CFG.name} пустой token."); sys.exit(1)
    c["tools"] = [t for t in c["tools"] if t in ALL_TOOLS]
    if c["edl"] == "edl":
        c["edl"] = find_edl()
    return c


def find_edl() -> str:
    """edl.exe ставится в Scripts того Python, которым запущен агент, — PATH для этого не нужен."""
    import sysconfig
    dirs = [Path(sys.executable).parent / "Scripts", Path(sys.executable).parent]
    for scheme in (f"{os.name}_user", None):
        try:
            dirs.append(Path(sysconfig.get_path("scripts", scheme) if scheme else sysconfig.get_path("scripts")))
        except KeyError:
            pass
    for d in dirs:
        for n in ("edl" + EXE, "edl"):
            if (d / n).is_file():
                return str(d / n)
    return "edl"


def http(c: dict, method: str, path: str, body: dict | None = None, timeout: float = 60) -> dict:
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(c["server"].rstrip("/") + path, data=data, method=method,
                                 headers={"Authorization": f"Bearer {c['token']}", "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return json.loads(r.read().decode())


def in_work(name: str) -> Path | None:
    """Файл только внутри папки стенда: без абсолютных путей, дисков и «..»."""
    if not name or name.startswith(("/", "\\")) or re.match(r"^[A-Za-z]:", name):
        return None
    p = (WORK / name).resolve()
    w = WORK.resolve()
    return p if p == w or w in p.parents else None


def safe_args(args: list[str]) -> tuple[list[str] | None, str]:
    """Аргументы, похожие на путь (с разделителем или расширением файла), должны лежать в папке стенда.
    Опции вида --loader=файл проверяются так же."""
    out = []
    for a in args:
        key, eq, val = a.partition("=") if a.startswith("-") else ("", "", a)
        if a.startswith("-") and not eq:
            out.append(a); continue
        looks_path = bool(re.search(r"[\\/]", val)) or bool(re.search(r"\.(img|bin|elf|mbn|xml|zip|raw|melf|txt|tar|gz|lz4|br|dat|apk)$", val, re.I))
        if looks_path:
            p = in_work(val)
            if p is None:
                return None, f"файл «{val}» вне папки стенда — можно только имена внутри неё"
            val = str(p)
        out.append(f"{key}={val}" if eq else val)
    return out, ""


def adb_args(args: list[str]) -> tuple[list[str] | None, str]:
    """У adb часть путей — на телефоне (/sdcard, /data): их не трогаем. Проверяем только файлы ПК:
    push <ПК…> <телефон>, pull <телефон…> [ПК], install/install-multiple/sideload <ПК…>."""
    opts = [i for i, a in enumerate(args) if a.startswith("-")]
    pos = [i for i in range(len(args)) if i not in opts]
    if not pos:
        return args, ""
    verb = args[pos[0]].lower()
    rest = pos[1:]
    local: list[int] = []
    if verb == "push":
        local = rest[:-1]
    elif verb == "pull":
        local = rest[1:] if len(rest) > 1 else []
    elif verb in ("install", "install-multiple", "sideload"):
        local = rest
    out = list(args)
    for i in local:
        p = in_work(args[i])
        if p is None:
            return None, f"файл «{args[i]}» вне папки стенда — можно только имена внутри неё"
        out[i] = str(p)
    if verb == "pull" and len(rest) == 1:
        out.append(str(WORK))          # без места — в папку стенда, не куда попало
    return out, ""


def list_usb() -> tuple[int, str]:
    try:
        import usb.core
    except ImportError:
        return 1, "нет pyusb: запусти install.bat"
    backend = None
    if os.name == "nt":                      # на Windows libusb нет в системе — берём ту, что везёт edl
        try:
            import importlib.util, usb.backend.libusb1
            places = [HERE / "libusb-1.0.dll"]
            spec = importlib.util.find_spec("edlclient")
            if spec and spec.origin:
                places.append(Path(spec.origin).parent / "Windows" / "libusb-1.0.dll")
            for dll in places:
                if dll.is_file():
                    backend = usb.backend.libusb1.get_backend(find_library=lambda _x, d=str(dll): d)
                    if backend:
                        break
        except Exception:
            backend = None
    try:
        devs = list(usb.core.find(find_all=True, backend=backend))
    except Exception as e:
        return 1, f"USB не читается: {type(e).__name__}: {e}"
    names = {(0x05C6, 0x9008): "телефон в EDL (Qualcomm 9008)", (0x18D1, 0x4EE0): "телефон в fastboot", (0x18D1, 0xD00D): "телефон в fastboot"}
    lines = [f"{d.idVendor:04x}:{d.idProduct:04x}" + (f"  ← {names[(d.idVendor, d.idProduct)]}" if (d.idVendor, d.idProduct) in names else "")
             for d in devs]
    return 0, "\n".join(lines) or "USB-устройств не видно"


def list_files() -> tuple[int, str]:
    WORK.mkdir(exist_ok=True)
    rows = []
    for p in sorted(WORK.rglob("*")):
        if p.is_file():
            rows.append(f"{p.relative_to(WORK)}  {p.stat().st_size // 1024} КБ")
    return 0, "\n".join(rows[-300:]) or "папка стенда пуста"


def download(c: dict, args: list[str]) -> tuple[int, str]:
    if len(args) < 2 or not args[0].startswith("https://"):
        return 1, "формат: скачать https://адрес имя_файла"
    dst = in_work(args[1])
    if dst is None:
        return 1, "имя файла — только внутри папки стенда"
    dst.parent.mkdir(parents=True, exist_ok=True)
    cap = int(c["download_max_mb"]) * 1024 * 1024
    got = 0
    try:
        with urllib.request.urlopen(urllib.request.Request(args[0], headers={"User-Agent": "stand-agent"}), timeout=60) as r, \
                open(dst, "wb") as f:
            while True:
                chunk = r.read(1 << 20)
                if not chunk:
                    break
                got += len(chunk)
                if got > cap:
                    f.close(); dst.unlink(missing_ok=True)
                    return 1, f"больше {c['download_max_mb']} МБ — остановлено"
                f.write(chunk)
    except Exception as e:
        dst.unlink(missing_ok=True)
        return 1, f"не скачалось: {type(e).__name__}: {e}"
    return 0, f"скачано: {dst.relative_to(WORK)} ({got // 1024} КБ)"


def run_tool(c: dict, tool: str, args: list[str]) -> tuple[int, str]:
    if tool == "edl" and args and args[0].lower() in EDL_BLOCKED:
        return 1, "edl server на стенде выключен (открывает порт на ПК)"
    if tool == "adb" and args and args[0].lower() in ADB_BLOCKED:
        return 1, f"adb {args[0]} на стенде выключен (сеть и туннели ПК)"
    if tool == "adb":
        safe, why = adb_args(args)
    else:
        safe, why = safe_args(args)
    if safe is None:
        return 1, why
    WORK.mkdir(exist_ok=True)
    try:
        p = subprocess.run([c[tool]] + safe, cwd=WORK, capture_output=True, timeout=int(c["timeout_min"]) * 60)
    except FileNotFoundError:
        return 1, f"не найдена программа {tool} ({c[tool]}): запусти install.bat"
    except subprocess.TimeoutExpired:
        return 1, f"{tool} не уложился в {c['timeout_min']} мин"
    out = (p.stdout + p.stderr).decode("utf-8", "replace")
    return p.returncode, ANSI.sub("", out)[-6000:]


def handle(c: dict, cmd: str) -> tuple[int, str]:
    try:
        parts = shlex.split(cmd, posix=True)
    except ValueError as e:
        return 1, f"не разобрать команду: {e}"
    if not parts:
        return 1, "пустая команда"
    tool, args = parts[0].lower(), parts[1:]
    if tool not in c["tools"]:
        return 1, f"на этом ПК «{tool}» не разрешён"
    if tool == "usb":
        return list_usb()
    if tool == "файлы":
        return list_files()
    if tool == "скачать":
        return download(c, args)
    return run_tool(c, tool, args)


def main() -> None:
    c = load_cfg()
    WORK.mkdir(exist_ok=True)
    print(f"Стенд на связи с {c['server']}. Можно: {', '.join(c['tools'])}. Папка: {WORK}. Остановить: Ctrl+C.")
    q = urllib.parse.urlencode({"wait": 25, "agent": os.environ.get("COMPUTERNAME", "pc"), "verbs": ",".join(c["tools"])})
    while True:
        try:
            job = http(c, "GET", f"/stand/next?{q}", timeout=40).get("job")
        except urllib.error.HTTPError as e:
            print(f"сервер ответил {e.code}" + (" — проверь token" if e.code == 401 else "")); time.sleep(15); continue
        except Exception as e:
            print(f"нет связи: {type(e).__name__}"); time.sleep(10); continue
        if not job:
            continue
        cmd = str(job.get("cmd") or job.get("verb") or "")
        print(time.strftime("%H:%M:%S"), "команда:", cmd)
        code, out = handle(c, cmd)
        print(out[-400:])
        for _ in range(3):
            try:
                http(c, "POST", "/stand/result", {"id": job["id"], "code": code, "out": out}); break
            except Exception as e:
                print(f"ответ не ушёл: {type(e).__name__}"); time.sleep(5)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\nСтенд остановлен.")
