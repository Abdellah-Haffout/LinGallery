# LinGallery

A beautiful and lightweight image gallery application for Linux. Organize, browse, and view your photo collection with an intuitive Material Design interface optimized for comfortable viewing.

## What is LinGallery?

LinGallery is a fast, user-friendly image viewer and organizer designed for Linux users who want a simple way to browse and manage their photo libraries. Unlike complex image editors, LinGallery focuses on what you really need: finding your photos quickly, viewing them beautifully, and doing basic organization tasks.

## Key Features

- **Browse Your Photos** - Organize images by album and folder structure
- **Beautiful Viewing** - Full-screen slideshow with smooth navigation
- **Clean Interface** - Modern design that's easy to understand and use
- **Dark Theme** - Reduces eye strain during extended browsing sessions
- **Quick Search** - Find your images without complicated menus
- **Edit Toolbar** - Basic image adjustments and rotation
- **Photo Information** - View EXIF data and image details
- **Works Everywhere** - Compatible with both Wayland and Xorg display servers

## Why Choose LinGallery?

- Lightweight and fast - no unnecessary bloat
- Designed specifically for Linux
- Respects your privacy - works offline, no cloud accounts needed
- Free and open source
- Material Design 3 interface - modern and consistent

## Installation

### Requirements

- Linux (Ubuntu, Fedora, Debian, Arch, or any Linux distribution)
- Python 3.8 or later

### Step 1: Install Python and Pip

Most Linux distributions come with Python pre-installed. To check if you have Python:

```bash
python3 --version
```

### Step 2: Get LinGallery

Clone or download this project to your computer:

```bash
git clone https://github.com/yourusername/lingallery.git
cd lingallery
```

### Step 3: Install Dependencies

Run this command in the lingallery folder:

```bash
pip install -r requirements.txt
```

This will install all the libraries LinGallery needs to run.

### Step 4: Run LinGallery

Start the application:

```bash
python3 src/main.py
```

The LinGallery window will open. Click on a folder to browse your images.

## How to Use

### Opening Your Photo Folder

1. Launch LinGallery
2. Use the file browser to navigate to your photo folder
3. Your images will appear in the gallery view

### Viewing Photos

- **Single Photo** - Click any image to view it in detail
- **Slideshow** - Click the slideshow button to view images automatically
- **Full Screen** - Press F11 or click the fullscreen button for immersive viewing
- **Navigation** - Use arrow keys or click navigation buttons to move between photos

### Basic Organization

- Create albums to group related photos
- Add tags to photos for easier searching
- View photo information including date taken and camera details

### Image Adjustments

- Rotate images left or right
- Use the edit toolbar for quick adjustments
- All changes are non-destructive (original files are never modified)

## Troubleshooting

### Application Won't Start

If you get an error when running `python3 src/main.py`:

1. Make sure you installed all requirements: `pip install -r requirements.txt`
2. Verify you have Python 3.8 or later: `python3 --version`
3. Try updating pip: `pip install --upgrade pip`

### Photos Not Showing

1. Make sure the folder contains image files (.jpg, .png, etc.)
2. Check that files have proper read permissions
3. Try refreshing the view with F5

### Performance Issues

1. Large photo libraries may take time to load initially
2. Close other applications to free up memory
3. Use SSD storage for faster browsing

## Getting Help

- Check the troubleshooting section above
- Report issues on GitHub
- Review existing questions and answers

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## About

LinGallery is developed as a modern, lightweight alternative to heavy image management software. It's built with love for Linux users who appreciate simplicity and elegance.

---

**Enjoy your photos with LinGallery!**
