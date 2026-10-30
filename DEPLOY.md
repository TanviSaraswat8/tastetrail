# Putting TasteTrail online (free)

You do **not** need Redis, MySQL or any other server. The app is one Java program with an embedded
database, so it runs anywhere Java or Docker runs.

## Option A – Render.com (recommended, free, about 10 minutes)

1. **Put the code on GitHub**
   - Sign in at https://github.com → **New repository** → name it `tastetrail` → Public → **Create repository**.
   - On the new repository page click **uploading an existing file**, drag in *everything inside* the
     `tt-java` folder (Dockerfile, render.yaml, src, web, lib, … – not the folder itself), then **Commit changes**.
2. **Deploy on Render**
   - Sign in at https://render.com with your GitHub account.
   - Click **New +** → **Blueprint** → choose the `tastetrail` repository → **Apply**.
     (Render reads `render.yaml` and builds the `Dockerfile` – the first build takes 3–5 minutes.)
   - When it shows **Live**, open the link at the top, e.g. `https://tastetrail-xxxx.onrender.com`.
3. **Before your viva**
   - The free plan sleeps after 15 minutes without visitors. Open the link **5 minutes before** your turn so it is awake
     (the first load after sleeping takes about a minute).
   - Every restart creates a fresh demo database, so the demo data is always clean.

## Option B – run it on your laptop (always works, no internet needed)

1. Install a JDK 17 or newer (e.g. https://adoptium.net → Temurin 21 → install).
2. Windows: double-click `run.bat`. macOS: open Terminal in the folder and run `./run.sh`.
3. Open http://localhost:8080

**Tip for the viva:** keep Option B running on your laptop as a backup even if you show the online link.

## Option C – any Docker host

```
docker build -t tastetrail .
docker run -p 8080:8080 -e JWT_SECRET=some-long-random-text tastetrail
```
