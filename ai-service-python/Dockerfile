# 1. Image de base : Utilisation d'une version légère de Python 3.9 pour optimiser la taille
FROM python:3.9-slim

# 2. Installation des dépendances système requises par OpenCV
# 'libgl1-mesa-glx' est nécessaire pour le traitement d'image (cv2) sous Linux et On vide le cache (rm -rf) immédiatement pour garder l'image légère
RUN apt-get update && apt-get install -y \
    libgl1 \
    libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

# 3. Définition du répertoire de travail à l'intérieur du conteneur
WORKDIR /app

# 4. Gestion des dépendances Python
# On copie d'abord le fichier requirements.txt pour profiter du cache Docker
COPY requirements.txt .

# Installation des paquets sans garder le cache pip 
RUN pip install --no-cache-dir -r requirements.txt

# Ajout spécifique d'OpenCV (version serveur/headless) pour la future fonctionnalité de Heatmap (Grad-CAM)
RUN pip install opencv-python-headless

# 5. Copie de l'ensemble du code source de l'application (app.py, modèles, etc.)
COPY . .

# 6. Exposition du port réseau utilisé par Flask
EXPOSE 5000

# 7. Commande de démarrage du service IA
CMD ["python", "app.py"]