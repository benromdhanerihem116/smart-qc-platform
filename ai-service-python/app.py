import numpy as np
from flask import Flask, request, jsonify
from flask_cors import CORS
from tensorflow.keras.models import load_model
from tensorflow.keras.preprocessing import image as keras_image
from tensorflow.keras.applications.mobilenet_v2 import MobileNetV2, preprocess_input, decode_predictions
import io

app = Flask(__name__)
CORS(app)

# 1. CHARGEMENT DES SYSTÈMES 
print("🏭 Initialisation du protocole de sécurité CORE-SCANNER...")

# Modèle généraliste (Vigile)
vigile_model = MobileNetV2(weights='imagenet')

# Modèle Expert (Votre IA Turbine)
try:
    industrial_model = load_model('industrial_model.h5')
    print(" Systèmes prêts.")
except Exception as e:
    print(f" Erreur modèle expert : {e}")

@app.route('/ai-predict', methods=['POST'])
def predict():
    if 'image' not in request.files:
        return jsonify({"error": "No image"}), 400
    
    file = request.files['image']
    img_bytes = file.read()

    #  ÉTAPE 1 : LE FILTRE VIGILE (Acceptation large) 
    img_v = keras_image.load_img(io.BytesIO(img_bytes), target_size=(224, 224))
    v_array = keras_image.img_to_array(img_v)
    v_array = np.expand_dims(v_array, axis=0)
    v_array = preprocess_input(v_array)

    v_preds = vigile_model.predict(v_array)
    v_decoded = decode_predictions(v_preds, top=5)[0] 
    
    # Mots-clés qui ressemblent à votre turbine (pour éviter le rejet injuste)
    labels_autorises = ['rotor', 'fan', 'metal', 'machine', 'engine', 'wheel', 'disk', 'coil', 'washer', 'hardware', 'vault']
    # Mots-clés de rejet strict (ce qui n'est ABSOLUMENT PAS une turbine)
    labels_interdits = ['wagon', 'car', 'apple', 'pomegranate', 'fruit', 'dog', 'person', 'furniture']

    detected_labels = [label[1].lower() for label in v_decoded]
    
    # On vérifie si l'image contient un élément technique
    est_technique = any(any(kw in label for kw in labels_autorises) for label in detected_labels)
    # On vérifie si l'IA est sûre qu'il s'agit d'un intrus (voiture, fruit...)
    est_intrus = any(any(rj in label for rj in labels_interdits) for label in detected_labels)

    if not est_technique or est_intrus:
        nom_objet = v_decoded[0][1].replace('_', ' ').upper()
        return jsonify({
            "status": "INVALID_OBJECT",
            "detected_as": nom_objet,
            "message": "OBJET NON RECONNU",
            "detail": f"Sécurité : L'objet '{nom_objet}' n'est pas une turbine ou une pièce de fonderie valide."
        })

    #  ÉTAPE 2 : L'EXPERTISE TECHNIQUE 
    img_ex = keras_image.load_img(io.BytesIO(img_bytes), target_size=(224, 224))
    x = keras_image.img_to_array(img_ex) / 255.0
    x = np.expand_dims(x, axis=0)

    score = float(industrial_model.predict(x)[0][0])
    
    # Seuil de décision (0.5)
    is_def = score < 0.5
    confiance = (1 - score if is_def else score) * 100

    return jsonify({
        "status": "SUCCESS",
        "is_defective": is_def,
        "prediction": round(confiance, 2),
        "message": "DÉFAUT DÉTECTÉ 🔴" if is_def else "PIÈCE CONFORME 🟢",
        "detail": "Anomalie de surface identifiée." if is_def else "La structure de la turbine est intègre."
    })

if __name__ == '__main__':
    app.run(port=5000)