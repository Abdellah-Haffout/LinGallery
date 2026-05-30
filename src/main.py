"""
LinGallery — Application entry point.
Handles Wayland/Xorg platform detection and launches the main window.
"""
import os
import sys

from PySide6.QtCore import Qt, QCoreApplication
from PySide6.QtWidgets import QApplication

from ui.main_window import MainWindow


def setup_environment():
    """Configure Qt platform settings for Wayland / Xorg compatibility."""
    session = os.environ.get("XDG_SESSION_TYPE", "").lower()
    if session == "wayland" and "QT_QPA_PLATFORM" not in os.environ:
        # Prefer native Wayland; xcb is the fallback on Xorg
        os.environ["QT_QPA_PLATFORM"] = "wayland;xcb"

    # Suppress Qt SVG warnings from system cursors / file dialog icons
    os.environ.setdefault("QT_LOGGING_RULES", "qt.svg=false")

    # Qt6 handles high-DPI automatically; no manual scaling flags needed
    QCoreApplication.setAttribute(Qt.AA_ShareOpenGLContexts, True)


def main():
    setup_environment()

    app = QApplication(sys.argv)
    app.setApplicationName("LinGallery")
    app.setApplicationVersion("1.0.0")
    app.setOrganizationName("LinGallery")
    app.setStyle("Fusion")  # Reliable cross-distro base

    window = MainWindow()
    window.show()

    sys.exit(app.exec())


if __name__ == "__main__":
    main()
