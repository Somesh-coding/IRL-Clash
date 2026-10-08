# IRL Clash

Location-based, no-database, two-player outdoor image battle.

Flow: GPS/manual coordinates -> nearby match -> 5 random missions -> timed camera/gallery upload -> Gemini judges all submitted images after round 5 -> winner.

## Backend
```powershell
cd backend
mvn spring-boot:run
```
Set Gemini key before starting:
```powershell
$env:GEMINI_API_KEY="YOUR_KEY"
```
Optional:
```powershell
$env:MATCH_RADIUS_METERS="100"
$env:GEMINI_MODEL="gemini-2.5-flash"
```

## Frontend
```powershell
cd frontend
npm install
npm run dev
```
Open http://localhost:5173.

## Two-player test
Use two browser windows and manual coordinates. Example:
A: 31.468500, 77.588000
B: 31.468600, 77.588100

Increase radius to 100m for testing if needed.

No database: presence, battles and image bytes exist only in JVM memory and disappear when Spring Boot restarts.
