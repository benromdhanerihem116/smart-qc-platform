@echo off
echo  DEMARRAGE DE LA SMART QC PLATFORM...


:: 1. Lancer Docker en arrière-plan
docker-compose up -d

:: 2. Attendre quelques secondes que le serveur soit prêt 
echo Attente du lancement des services...
timeout /t 20

:: 3. Ouvrir le navigateur automatiquement
echo Ouverture de l'interface...
start http://localhost:3000
echo  PRET !
pause