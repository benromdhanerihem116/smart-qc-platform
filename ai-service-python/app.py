import os
import io
import numpy as np
import cv2
import base64
from flask import Flask, request, jsonify
from flask_cors import CORS
import tensorflow as tf
from tensorflow.keras.models import load_model
from tensorflow.keras.preprocessing import image
from tensorflow.keras.applications.mobilenet_v2 import MobileNetV2, preprocess_input, decode_predictions

app = Flask(__name__)
CORS(app)

# CONFIGURATION 
MODEL_PATH = "model.h5"
expert_model = None
vigilante_model = None

# LISTE BLANCHE (WHITELIST) DES TERMES INDUSTRIELS AUTORISÉS
ALLOWED_INDUSTRIAL_TERMS = [
    # Matières et concepts généraux
    'steel', 'metal', 'iron', 'aluminum', 'chrome', 'alloy', 'hard', 'tool',
    
    # Formes industrielles et mécaniques (Vues par ImageNet)
    'disc', 'brake', 'wheel', 'rim', 'cylinder', 'engine', 'motor', 
    'radiator', 'grille', 'heater', 'shield', 'buckle', 'hook', 
    'manhole', 'cover', 'strainer', 'thimble', 'washer', 'screw', 
    'nail', 'bolt', 'drill', 'plane', 'hammer', 'wrench', 'projectile',
    'lock', 'key', 'mechanism', 'gears', 'pump', 'switch', 'puck',
    'casting', 'mould', 'turnstile', 'safe', 'vault', 'magnet',
    
    'coil', 'spiral', 'bearing', 'spring', 'suspension' ]

def load_ai_models():
    global expert_model, vigilante_model
    
    # 1. Chargement de l'Expert (Ton modèle fine-tuné)
    if os.path.exists(MODEL_PATH):
        try:
            expert_model = load_model(MODEL_PATH)
            print(f" [Niveau 2] Expert QC chargé ({MODEL_PATH})")
        except Exception as e:
            print(f" Erreur chargement Expert : {e}")
    else:
        print(" Modèle Expert introuvable.")

    # 2. Chargement du Vigile (MobileNetV2 Standard - ImageNet)
    try:
        vigilante_model = MobileNetV2(weights='imagenet')
        print(f" [Niveau 1] Vigile Sémantique chargé (ImageNet)")
    except Exception as e:
        print(f" Erreur chargement Vigile : {e}")

load_ai_models()

def get_gradcam_heatmap(img_array, model, last_conv_layer_name="out_relu"):
    try:
        grad_model = tf.keras.models.Model(
            [model.inputs],
            [model.get_layer(last_conv_layer_name).output, model.output]
        )
        with tf.GradientTape() as tape:
            last_conv_layer_output, preds = grad_model(img_array)
            class_channel = preds[:, 0]
        grads = tape.gradient(class_channel, last_conv_layer_output)
        pooled_grads = tf.reduce_mean(grads, axis=(0, 1, 2))
        last_conv_layer_output = last_conv_layer_output[0]
        heatmap = last_conv_layer_output @ pooled_grads[..., tf.newaxis]
        heatmap = tf.squeeze(heatmap)
        heatmap = tf.maximum(heatmap, 0) / tf.math.reduce_max(heatmap)
        return heatmap.numpy()
    except:
        return np.zeros((224, 224))

@app.route('/ai-predict', methods=['POST'])
def predict():
    global expert_model, vigilante_model
    if not expert_model or not vigilante_model: load_ai_models()
    
    if 'image' not in request.files: return jsonify({'error': 'No image'}), 400

    try:
        file = request.files['image']
        img_bytes = io.BytesIO(file.read())
        
        # 1. Préparation de l'image (Commune aux deux modèles)
        original_img = image.load_img(img_bytes, target_size=(224, 224))
        img_array = image.img_to_array(original_img)
        img_batch = np.expand_dims(img_array, axis=0)
        img_preprocessed = preprocess_input(img_batch.copy())

        #  NIVEAU 1 : LE VIGILE (Filtrage Sémantique par Liste Blanche)
        preds_vigile = vigilante_model.predict(img_preprocessed)
        # On récupère le Top 3 des objets détectés pour être sûr de ne rien rater
        decoded_preds = decode_predictions(preds_vigile, top=3)[0]
        
        # On crée une liste des mots détectés (ex: ['coil', 'radiator', 'wheel'])
        detected_keywords = [p[1].lower() for p in decoded_preds]
        top_confidence = decoded_preds[0][2]

        print(f" VIGILE ANALYSE : {detected_keywords} (Top Conf: {top_confidence:.2f})")

        is_industrial = False
        for detected_word in detected_keywords:
            # Si un mot autorisé (ex: "coil") est contenu dans le mot détecté (ex: "magnetic_coil")
            if any(allowed in detected_word for allowed in ALLOWED_INDUSTRIAL_TERMS):
                is_industrial = True
                break

        # SI CE N'EST PAS INDUSTRIEL -> ON REJETTE
        # On garde le seuil de confiance à 0.15 pour filtrer les vrais intrus (chats, paysages)
        if not is_industrial and top_confidence > 0.15:
            rejected_object = decoded_preds[0][1] # Le nom principal détecté
            print(f"  REJET SÉMANTIQUE : '{rejected_object}' n'est pas autorisé.")
            
            return jsonify({
                'error': 'OBJECT_MISMATCH',
                'detected_object': rejected_object,
                'message': f"Image rejetée : L'IA a identifié '{rejected_object}' qui ne correspond pas au périmètre industriel autorisé."
            }), 400


        #  NIVEAU 2 : L'EXPERT (Détection de Défaut)
        preds = expert_model.predict(img_preprocessed)
        raw_score = float(preds[0][0])
        
        # Logique : Defect=0, OK=1 
        if raw_score < 0.5:
            is_defective = True
            confidence = (1 - raw_score) * 100
            status_text = "NOK (Défaut)"
        else:
            is_defective = False
            confidence = raw_score * 100
            status_text = "OK (Conforme)"

        print(f" EXPERT JUGE : Score={raw_score:.4f} | Décision={status_text}")

        # Heatmap (XAI)
        heatmap = get_gradcam_heatmap(img_preprocessed, expert_model, "out_relu")
        heatmap = np.uint8(255 * heatmap)
        heatmap = cv2.applyColorMap(heatmap, cv2.COLORMAP_JET)
        heatmap = cv2.resize(heatmap, (224, 224))
        original_cv = cv2.cvtColor(np.array(original_img), cv2.COLOR_RGB2BGR)
        superimposed_img = cv2.addWeighted(original_cv, 0.6, heatmap, 0.4, 0)
        _, buffer = cv2.imencode('.jpg', superimposed_img)
        heatmap_b64 = base64.b64encode(buffer).decode('utf-8')

        return jsonify({
            'status': 'SUCCESS',
            'prediction': round(confidence, 2),
            'is_defective': is_defective,
            'heatmap': heatmap_b64,
            'object_type': decoded_preds[0][1] # On renvoie aussi ce que le vigile a vu
        })

    except Exception as e:
        print(f"ERREUR: {e}")
        return jsonify({'error': str(e)}), 500

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000)