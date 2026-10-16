# DayFlow Planner ⏱️

Daily productivity dashboard for developers & designers: a millisecond-precision clock plus an interactive, live-updating task board. Built with **plain Java (Swing)**.

## Features (v1.0)
- Real-time clock with milliseconds (`AnimationTimer`, ~60 FPS)
- Dark-theme dashboard
- Add / complete / delete tasks (priority + optional time)
- Per-day progress bar with smooth animation
- Date filter (`DatePicker`) + live search
- Auto-save to `tasks_data.dat`, auto-load on startup
- Performance settings panel (clock FPS, milliseconds on/off, animations, background power saving)

## Run
Plain Java (Swing) - **no external libraries**. Requires only JDK 17+.

```bash
javac -d out src/Main.java
java -cp out Main
```

## Roadmap
- **v2.0** Pomodoro timer, notifications, weekly analytics chart
- **v3.0** SQLite/MySQL, PDF/CSV export, Arabic/English UI
