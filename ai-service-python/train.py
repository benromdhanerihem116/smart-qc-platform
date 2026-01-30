import tensorflow as tf
from tensorflow.keras.preprocessing.image import ImageDataGenerator
from tensorflow.keras.applications.mobilenet_v2 import MobileNetV2, preprocess_input
from tensorflow.keras.layers import Dense, GlobalAveragePooling2D
from tensorflow.keras.models import Model
import os

# CONFIGURATION
base_dir = 'casting_data/casting_data/'
train_dir = os.path.join(base_dir, 'train')
test_dir = os.path.join(base_dir, 'test')
# 1. PRÉPARATION DES DONNÉES
# Cela met les pixels entre -1 et 1, le format natif de MobileNet
train_datagen = ImageDataGenerator(
    preprocessing_function=preprocess_input, 
    rotation_range=20, 
    zoom_range=0.2, 
    horizontal_flip=True
)

test_datagen = ImageDataGenerator(preprocessing_function=preprocess_input)

print(" Chargement des données...")
train_generator = train_datagen.flow_from_directory(
    train_dir, target_size=(224, 224), batch_size=32, class_mode='binary'
)

print(" ORDRE DES CLASSES DÉTECTÉ : ", train_generator.class_indices)

validation_generator = test_datagen.flow_from_directory(
    test_dir, target_size=(224, 224), batch_size=32, class_mode='binary'
)

# 2. MODÈLE
base_model = MobileNetV2(weights='imagenet', include_top=False, input_shape=(224, 224, 3))
base_model.trainable = False

x = base_model.output
x = GlobalAveragePooling2D()(x)
x = Dense(128, activation='relu')(x)
# Sigmoid : 0 = Classe 0 (Defect), 1 = Classe 1 (OK)
predictions = Dense(1, activation='sigmoid')(x) 

model = Model(inputs=base_model.input, outputs=predictions)

# 3. ENTRAÎNEMENT
print(" Début de l'entraînement...")
model.compile(optimizer='adam', loss='binary_crossentropy', metrics=['accuracy'])

model.fit(train_generator, epochs=5, validation_data=validation_generator)

# 4. SAUVEGARDE
model.save('model.h5') 
print(" Modèle 'model.h5' sauvegardé  !")