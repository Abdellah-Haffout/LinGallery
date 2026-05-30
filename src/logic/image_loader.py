import os
from PySide6.QtCore import QObject, QRunnable, Signal
from PySide6.QtGui import QImage, QImageReader

class ImageLoaderSignals(QObject):
    finished = Signal(str, QImage)
    error = Signal(str, str)

class ImageLoaderTask(QRunnable):
    """Background task to load an image without blocking the UI."""
    def __init__(self, image_path, target_size=None):
        super().__init__()
        self.image_path = image_path
        self.target_size = target_size
        self.signals = ImageLoaderSignals()

    def run(self):
        if not os.path.exists(self.image_path):
            self.signals.error.emit(self.image_path, "File does not exist")
            return
            
        reader = QImageReader(self.image_path)
        reader.setAutoTransform(True) # Handle EXIF rotation
        
        # If target size is set (e.g., for thumbnails), scale during decoding for speed
        if self.target_size:
            reader.setScaledSize(self.target_size)
            
        image = reader.read()
        
        if image.isNull():
            self.signals.error.emit(self.image_path, reader.errorString())
        else:
            self.signals.finished.emit(self.image_path, image)
