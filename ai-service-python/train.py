import tensorflow as tf
from tensorflow.keras.preprocessing.image import ImageDataGenerator
from tensorflow.keras.applications import MobileNetV2
from tensorflow.keras.layers import Dense, GlobalAveragePooling2D
from tensorflow.keras.models import Model
import os

# CONFIGURATION
base_dir = 'casting_data/casting_data/'
train_dir = os.path.join(base_dir, 'train')
test_dir = os.path.join(base_dir, 'test')

# 1. PRÉPARATION DES DONNÉES (Data Augmentation)
# On booste les données en créant des variations (zoom, rotation) pour rendre l'IA plus forte
train_datagen = ImageDataGenerator(rescale=1./255, rotation_range=20, zoom_range=0.2, horizontal_flip=True)
test_datagen = ImageDataGenerator(rescale=1./255)

train_generator = train_datagen.flow_from_directory(
    train_dir, target_size=(224, 224), batch_size=32, class_mode='binary'
)

validation_generator = test_datagen.flow_from_directory(
    test_dir, target_size=(224, 224), batch_size=32, class_mode='binary'
)

# 2. CHIRURGIE DU CERVEAU (Transfer Learning)
# On télécharge MobileNetV2, mais SANS la dernière couche (include_top=False)
base_model = MobileNetV2(weights='imagenet', include_top=False, input_shape=(224, 224, 3))

# On "gèle" les connaissances de base (pour ne pas oublier comment voir les formes)
base_model.trainable = False

# 3. GREFFE DU NOUVEAU CERVEAU
x = base_model.output
x = GlobalAveragePooling2D()(x)
x = Dense(128, activation='relu')(x) # Une couche de neurones pour réfléchir
predictions = Dense(1, activation='sigmoid')(x) # La sortie finale (0=Bon, 1=Défaut)

model = Model(inputs=base_model.input, outputs=predictions)

# 4. ENTRAÎNEMENT
print(" Début de l'entraînement industriel...")
model.compile(optimizer='adam', loss='binary_crossentropy', metrics=['accuracy'])

# On entraîne sur 5 "époques" (il va lire tout le dossier 5 fois)
model.fit(train_generator, epochs=5, validation_data=validation_generator)

# 5. SAUVEGARDE
model.save('industrial_model.h5')
print(" Modèle 'industrial_model.h5' sauvegardé avec succès !")