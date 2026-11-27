# -*- coding: utf-8 -*-
"""
Pocket Agent for Windows — ایجنت جیبی (نسخه ویندوز)

A desktop edition of Pocket Agent (AnishtayiN/pocket-agent) that runs on
Windows 7 through 11. Pure Python standard library (tkinter UI), so the
frozen .exe has no third-party runtime dependencies.

Brain: Pollinations text API (keyless, OpenAI-compatible, reasoning model).
Tools (executed locally, driven by a simple fenced-JSON protocol so any
chat model can use them):
  web_search / web_fetch (Keenable public), generate_image (Pollinations),
  run_python (self-subprocess), read_file / write_file (workspace),
  remember (long-term memory), get_time.

Data lives in %APPDATA%\\PocketAgent (settings, memory, history, workspace).
"""

import json
import os
import queue
import re
import subprocess
import sys
import threading
import time
import urllib.parse
import urllib.request
from datetime import datetime

APP_NAME = "PocketAgent"
POLLINATIONS_CHAT = "https://text.pollinations.ai/openai"
POLLINATIONS_MODEL = "openai"
KEENABLE_SEARCH = "https://api.keenable.ai/v1/search/public"
KEENABLE_FETCH = "https://api.keenable.ai/v1/fetch/public"
MAX_TOOL_ROUNDS = 8
HTTP_TIMEOUT = 120


# --------------------------------------------------------------------------
# Self-subprocess code runner:  PocketAgent.exe --run-code file.py file.out
# --------------------------------------------------------------------------

def run_code_mode(code_path, out_path):
    import io
    import traceback

    buffer = io.StringIO()
    old_out, old_err = sys.stdout, sys.stderr
    sys.stdout = buffer
    sys.stderr = buffer
    code = 0
    try:
        with io.open(code_path, "r", encoding="utf-8") as f:
            source = f.read()
        globals_dict = {"__name__": "__main__", "__file__": code_path}
        exec(compile(source, code_path, "exec"), globals_dict)
    except BaseException:
        traceback.print_exc()
        code = 1
    finally:
        sys.stdout = old_out
        sys.stderr = old_err
    with io.open(out_path, "w", encoding="utf-8") as f:
        f.write(buffer.getvalue())
    return code


# --------------------------------------------------------------------------
# Storage
# --------------------------------------------------------------------------

def data_dir():
    base = os.environ.get("APPDATA") or os.path.expanduser("~")
    path = os.path.join(base, APP_NAME)
    if not os.path.isdir(path):
        os.makedirs(path)
    return path


def workspace_dir():
    path = os.path.join(data_dir(), "workspace")
    if not os.path.isdir(path):
        os.makedirs(path)
    return path


def load_json(name, default):
    try:
        with open(os.path.join(data_dir(), name), "r", encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return default


def save_json(name, value):
    try:
        with open(os.path.join(data_dir(), name), "w", encoding="utf-8") as f:
            json.dump(value, f, ensure_ascii=False, indent=1)
    except Exception:
        pass


# --------------------------------------------------------------------------
# HTTP helpers
# --------------------------------------------------------------------------

def http_json(url, payload, timeout=HTTP_TIMEOUT):
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=data,
        headers={
            "Content-Type": "application/json",
            "User-Agent": "PocketAgent-Windows/6.1",
        },
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8", "replace"))


def http_get_bytes(url, timeout=120):
    req = urllib.request.Request(
        url, headers={"User-Agent": "PocketAgent-Windows/6.1"}
    )
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return resp.read()


# --------------------------------------------------------------------------
# Tools
# --------------------------------------------------------------------------

def tool_web_search(args):
    query = (args.get("query") or "").strip()
    if not query:
        return "خطا: query خالی است"
    result = http_json(KEENABLE_SEARCH, {"query": query, "max_results": 6})
    items = result.get("results") or result.get("data") or []
    if not items:
        return "نتیجه‌ای پیدا نشد."
    lines = []
    for i, item in enumerate(items[:6], 1):
        title = item.get("title") or ""
        url = item.get("url") or ""
        snippet = (item.get("snippet") or item.get("content") or "")[:400]
        date = item.get("published_date") or item.get("date") or ""
        lines.append("%d. %s\n%s\n%s %s" % (i, title, url, snippet, date))
    return "\n\n".join(lines)[:9000]


def tool_web_fetch(args):
    url = (args.get("url") or "").strip()
    if not url:
        return "خطا: url خالی است"
    result = http_json(KEENABLE_FETCH, {"urls": [url]})
    pages = result.get("results") or result.get("data") or []
    if pages:
        content = pages[0].get("content") or pages[0].get("text") or ""
        if content:
            return content[:12000]
    return "محتوای صفحه خوانده نشد."


def tool_generate_image(args):
    prompt = (args.get("prompt") or "").strip()
    if not prompt:
        return "خطا: prompt خالی است"
    url = (
        "https://image.pollinations.ai/prompt/"
        + urllib.parse.quote(prompt)
        + "?width=1024&height=1024&nologo=true"
    )
    raw = http_get_bytes(url, timeout=180)
    folder = os.path.join(workspace_dir(), "images")
    if not os.path.isdir(folder):
        os.makedirs(folder)
    path = os.path.join(folder, "img_%d.png" % int(time.time()))
    with open(path, "wb") as f:
        f.write(raw)
    try:
        os.startfile(path)  # noqa: S606 - open the image for the user
    except Exception:
        pass
    return "تصویر ساخته و باز شد: %s" % path


def tool_run_python(args):
    code = args.get("code") or ""
    if not code.strip():
        return "خطا: code خالی است"
    folder = workspace_dir()
    stamp = int(time.time() * 1000)
    code_path = os.path.join(folder, "run_%d.py" % stamp)
    out_path = code_path + ".out"
    with open(code_path, "w", encoding="utf-8") as f:
        f.write(code)
    if getattr(sys, "frozen", False):
        cmd = [sys.executable, "--run-code", code_path, out_path]
    else:
        cmd = [sys.executable, os.path.abspath(__file__),
               "--run-code", code_path, out_path]
    try:
        proc = subprocess.run(
            cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            timeout=30,
            cwd=folder,
        )
        rc = proc.returncode
    except subprocess.TimeoutExpired:
        return "خطا: اجرای کد بیش از ۳۰ ثانیه طول کشید و متوقف شد."
    except Exception as exc:
        return "خطا در اجرا: %s" % exc
    output = ""
    try:
        with open(out_path, "r", encoding="utf-8") as f:
            output = f.read()
    except Exception:
        pass
    output = output.strip()
    if len(output) > 6000:
        output = output[:6000] + "\n…(بریده شد)"
    if not output:
        output = "(بدون خروجی) — کد با موفقیت اجرا شد" if rc == 0 else "(بدون خروجی)"
    return "خروجی اجرا (کد بازگشت %d):\n%s" % (rc, output)


def tool_write_file(args):
    name = os.path.basename((args.get("name") or "").strip())
    content = args.get("content")
    if not name or content is None:
        return "خطا: name یا content خالی است"
    path = os.path.join(workspace_dir(), name)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    return "فایل ذخیره شد: %s (%d نویسه)" % (path, len(content))


def tool_read_file(args):
    name = os.path.basename((args.get("name") or "").strip())
    if not name:
        return "خطا: name خالی است"
    path = os.path.join(workspace_dir(), name)
    try:
        with open(path, "r", encoding="utf-8") as f:
            return f.read()[:12000]
    except Exception as exc:
        return "خطا در خواندن فایل: %s" % exc


TOOLS = {
    "web_search": tool_web_search,
    "web_fetch": tool_web_fetch,
    "generate_image": tool_generate_image,
    "run_python": tool_run_python,
    "write_file": tool_write_file,
    "read_file": tool_read_file,
}

TOOL_BLOCK_RE = re.compile(r"```tool\s*(\{.*?\})\s*```", re.DOTALL)


def system_prompt(agent_name, memory_facts):
    memory_text = "\n".join("- " + fact for fact in memory_facts) or "(خالی)"
    return (
        "تو «%s» هستی، یک ایجنت هوشمند فارسی‌زبان روی ویندوز کاربر. "
        "دقیق، کامل و صادق جواب بده؛ اگر چیزی را نمی‌دانی، از ابزارها استفاده کن "
        "و هرگز اطلاعات ساختگی (عدد، تاریخ، لینک) نساز.\n\n"
        "حافظه بلندمدت تو درباره کاربر:\n%s\n\n"
        "ابزارها: برای استفاده از ابزار، پاسخ را فقط با یک بلوک به این شکل بده "
        "و هیچ متن دیگری ننویس:\n"
        "```tool\n{\"name\": \"web_search\", \"args\": {\"query\": \"...\"}}\n```\n"
        "ابزارهای موجود:\n"
        "- web_search {query}: جستجوی وب (برای هر سؤال واقعی/خبری حتماً استفاده کن)\n"
        "- web_fetch {url}: خواندن کامل یک صفحه وب\n"
        "- generate_image {prompt}: ساخت تصویر از توضیح انگلیسی\n"
        "- run_python {code}: اجرای کد پایتون روی همین کامپیوتر و گرفتن خروجی؛ "
        "برای کدنویسی: کد را اجرا کن، اگر خطا داد اصلاح و دوباره اجرا کن\n"
        "- write_file {name, content}: ذخیره فایل در پوشه کاری\n"
        "- read_file {name}: خواندن فایل از پوشه کاری\n"
        "- remember {fact}: ذخیره یک واقعیت در حافظه بلندمدت\n"
        "- get_time {}: تاریخ و ساعت فعلی\n"
        "بعد از دیدن نتیجه ابزار، یا ابزار بعدی را صدا بزن یا پاسخ نهایی کامل را بنویس."
        % (agent_name, memory_text)
    )


# --------------------------------------------------------------------------
# Agent loop
# --------------------------------------------------------------------------

class Agent(object):
    def __init__(self, settings, memory):
        self.settings = settings
        self.memory = memory

    def chat_once(self, messages):
        payload = {
            "model": POLLINATIONS_MODEL,
            "messages": messages,
        }
        result = http_json(POLLINATIONS_CHAT, payload)
        choice = (result.get("choices") or [{}])[0]
        message = choice.get("message") or {}
        return (message.get("content") or ""), (message.get("reasoning") or "")

    def run(self, history, on_status):
        """history: list of {role, content} without system. Yields events via
        on_status(kind, payload). Returns final answer text."""
        agent_name = self.settings.get("agent_name") or "جیبی"
        messages = [{"role": "system",
                     "content": system_prompt(agent_name, self.memory)}]
        messages += history
        reasoning_all = ""
        for _round in range(MAX_TOOL_ROUNDS):
            content, reasoning = self.chat_once(messages)
            if reasoning:
                reasoning_all += reasoning + "\n"
            match = TOOL_BLOCK_RE.search(content or "")
            if not match:
                return content.strip(), reasoning_all.strip()
            try:
                call = json.loads(match.group(1))
                name = call.get("name") or ""
                args = call.get("args") or {}
            except Exception:
                return content.strip(), reasoning_all.strip()
            on_status("tool", name)
            result_text = self.execute_tool(name, args)
            messages.append({"role": "assistant", "content": content})
            messages.append({
                "role": "user",
                "content": "نتیجه ابزار %s:\n%s" % (name, result_text),
            })
        return ("به سقف دفعات استفاده از ابزار رسیدم؛ "
                "همین‌جا جمع‌بندی می‌کنم: " + (content or "")), reasoning_all.strip()

    def execute_tool(self, name, args):
        if name == "remember":
            fact = (args.get("fact") or "").strip()
            if fact and fact not in self.memory:
                self.memory.append(fact)
                save_json("memory.json", self.memory)
            return "در حافظه ذخیره شد."
        if name == "get_time":
            return datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        func = TOOLS.get(name)
        if func is None:
            return "ابزار ناشناخته: %s" % name
        try:
            return func(args)
        except Exception as exc:
            return "خطای ابزار %s: %s" % (name, exc)


# --------------------------------------------------------------------------
# UI
# --------------------------------------------------------------------------

def start_ui():
    import tkinter as tk
    from tkinter import filedialog, messagebox, scrolledtext, simpledialog

    settings = load_json("settings.json", {})
    if "agent_name" not in settings:
        settings["agent_name"] = "جیبی"
    memory = load_json("memory.json", [])
    history = load_json("history.json", [])
    agent = Agent(settings, memory)

    root = tk.Tk()
    root.title("ایجنت جیبی — Pocket Agent")
    root.geometry("860x640")
    root.minsize(640, 480)

    bg = "#FAFAF8"
    accent = "#0E7C9C"
    root.configure(bg=bg)
    font_main = ("Tahoma", 11)
    font_small = ("Tahoma", 9)

    # header
    header = tk.Frame(root, bg=bg)
    header.pack(fill="x", padx=12, pady=(10, 4))
    title_var = tk.StringVar(value=settings["agent_name"])
    tk.Label(header, textvariable=title_var, font=("Tahoma", 15, "bold"),
             bg=bg, fg="#1C2321").pack(side="right")
    status_var = tk.StringVar(value="آماده")
    tk.Label(header, textvariable=status_var, font=font_small,
             bg=bg, fg="#6B7280").pack(side="right", padx=8)

    def on_new_chat():
        del history[:]
        save_json("history.json", history)
        chat.configure(state="normal")
        chat.delete("1.0", "end")
        chat.configure(state="disabled")

    def on_memory():
        win = tk.Toplevel(root)
        win.title("حافظه بلندمدت ایجنت")
        win.geometry("520x380")
        tk.Label(win, text="هر واقعیت در یک خط:",
                 font=font_small).pack(anchor="e", padx=10, pady=6)
        box = scrolledtext.ScrolledText(win, font=font_main, wrap="word")
        box.pack(fill="both", expand=True, padx=10)
        box.insert("1.0", "\n".join(memory))

        def save_mem():
            facts = [ln.strip() for ln in
                     box.get("1.0", "end").splitlines() if ln.strip()]
            del memory[:]
            memory.extend(facts)
            save_json("memory.json", memory)
            win.destroy()

        tk.Button(win, text="ذخیره", font=font_main,
                  command=save_mem).pack(pady=8)

    def on_rename():
        name = simpledialog.askstring(
            "نام ایجنت", "نام ایجنت چه باشد؟",
            initialvalue=settings.get("agent_name") or "جیبی", parent=root)
        if name and name.strip():
            settings["agent_name"] = name.strip()
            save_json("settings.json", settings)
            title_var.set(settings["agent_name"])

    buttons = tk.Frame(root, bg=bg)
    buttons.pack(fill="x", padx=12, pady=(0, 6))
    tk.Button(buttons, text="گفت‌وگوی جدید", font=font_small,
              command=on_new_chat).pack(side="right", padx=2)
    tk.Button(buttons, text="حافظه", font=font_small,
              command=on_memory).pack(side="right", padx=2)
    tk.Button(buttons, text="نام ایجنت", font=font_small,
              command=on_rename).pack(side="right", padx=2)

    chat = scrolledtext.ScrolledText(
        root, font=font_main, wrap="word", state="disabled",
        bg="#FFFFFF", fg="#1C2321", padx=10, pady=8, relief="flat")
    chat.pack(fill="both", expand=True, padx=12)
    chat.tag_configure("user_name", foreground=accent,
                       font=("Tahoma", 10, "bold"), justify="right")
    chat.tag_configure("user_text", justify="right")
    chat.tag_configure("agent_name", foreground="#155E75",
                       font=("Tahoma", 10, "bold"), justify="right")
    chat.tag_configure("agent_text", justify="right")
    chat.tag_configure("reasoning", foreground="#9CA3AF",
                       font=("Tahoma", 9), justify="right")
    chat.tag_configure("system", foreground="#B45309",
                       font=font_small, justify="right")

    def append(role, name, text, reasoning=""):
        chat.configure(state="normal")
        if role == "user":
            chat.insert("end", name + "\n", "user_name")
            chat.insert("end", text + "\n\n", "user_text")
        elif role == "agent":
            chat.insert("end", name + "\n", "agent_name")
            if reasoning:
                chat.insert("end", reasoning.strip() + "\n", "reasoning")
            chat.insert("end", text + "\n\n", "agent_text")
        else:
            chat.insert("end", text + "\n\n", "system")
        chat.see("end")
        chat.configure(state="disabled")

    for msg in history:
        if msg.get("role") == "user":
            append("user", "شما", msg.get("content") or "")
        elif msg.get("role") == "assistant":
            append("agent", settings["agent_name"], msg.get("content") or "")

    # input row
    bottom = tk.Frame(root, bg=bg)
    bottom.pack(fill="x", padx=12, pady=10)
    entry = tk.Text(bottom, font=font_main, height=3, wrap="word")
    entry.pack(side="right", fill="x", expand=True)
    send_btn = tk.Button(bottom, text="ارسال", font=font_main,
                         bg=accent, fg="white", width=8)
    send_btn.pack(side="right", padx=(0, 6))
    attach_btn = tk.Button(bottom, text="پیوست فایل", font=font_small)
    attach_btn.pack(side="right", padx=(0, 6))

    events = queue.Queue()
    state = {"busy": False, "t0": 0.0}

    def attach():
        path = filedialog.askopenfilename(parent=root)
        if not path:
            return
        try:
            with open(path, "r", encoding="utf-8", errors="replace") as f:
                content = f.read()
        except Exception as exc:
            messagebox.showerror("خطا", "خواندن فایل ممکن نشد: %s" % exc)
            return
        note = "فایل پیوست شد: %s (%d نویسه)" % (
            os.path.basename(path), len(content))
        entry.insert("1.0", "[محتوای فایل %s]\n%s\n\n" % (
            os.path.basename(path), content[:15000]))
        status_var.set(note)

    attach_btn.configure(command=attach)

    def set_busy(busy):
        state["busy"] = busy
        send_btn.configure(state="disabled" if busy else "normal")
        if busy:
            state["t0"] = time.time()
            tick_status()
        else:
            status_var.set("آماده")

    def tick_status():
        if not state["busy"]:
            return
        elapsed = int(time.time() - state["t0"])
        status_var.set("در حال فکر کردن… %d ثانیه" % elapsed)
        root.after(500, tick_status)

    def on_status(kind, payload):
        if kind == "tool":
            names = {
                "web_search": "جستجوی وب",
                "web_fetch": "خواندن صفحه",
                "generate_image": "ساخت تصویر",
                "run_python": "اجرای کد پایتون",
                "write_file": "ذخیره فایل",
                "read_file": "خواندن فایل",
                "remember": "ذخیره در حافظه",
                "get_time": "ساعت",
            }
            events.put(("status", "ابزار: " + names.get(payload, payload)))

    def send():
        if state["busy"]:
            return
        text = entry.get("1.0", "end").strip()
        if not text:
            return
        entry.delete("1.0", "end")
        append("user", "شما", text)
        history.append({"role": "user", "content": text})
        set_busy(True)

        def worker():
            try:
                answer, reasoning = agent.run(list(history), on_status)
                events.put(("answer", (answer, reasoning)))
            except Exception as exc:
                events.put(("error", str(exc)))

        threading.Thread(target=worker, daemon=True).start()

    def poll():
        try:
            while True:
                kind, payload = events.get_nowait()
                if kind == "answer":
                    answer, reasoning = payload
                    history.append({"role": "assistant", "content": answer})
                    save_json("history.json", history[-60:])
                    append("agent", settings["agent_name"], answer, reasoning)
                    set_busy(False)
                elif kind == "error":
                    append("system", "", "خطا: %s" % payload)
                    set_busy(False)
                elif kind == "status":
                    status_var.set(payload)
        except queue.Empty:
            pass
        root.after(120, poll)

    send_btn.configure(command=send)

    def on_enter(event):
        if event.state & 0x1:  # Shift+Enter = newline
            return None
        send()
        return "break"

    entry.bind("<Return>", on_enter)
    entry.focus_set()
    poll()
    root.mainloop()


def main():
    if len(sys.argv) >= 4 and sys.argv[1] == "--run-code":
        sys.exit(run_code_mode(sys.argv[2], sys.argv[3]))
    start_ui()


if __name__ == "__main__":
    main()
