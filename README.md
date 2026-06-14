# LinGallery

A desktop image gallery and viewer for Linux. It scans your photo folders, organizes images into albums, and gives you a full-screen viewer with editing tools.

> **Project status:** Under active development. First release coming soon.

## License

LinGallery is free software released under the GNU General Public License v3.0 (GPL-3.0). This means you are free to run the program for any purpose, study how it works, change it to do what you want, and share copies with others. If you distribute modified versions, you must also release your changes under the same license. There is no warranty -- the program is provided as-is.

The full license text is in the [LICENSE](LICENSE) file.

## Table of Contents

- [What is LinGallery?](#what-is-lingallery)
- [What it can do](#what-it-can-do)
- [How to use it](#how-to-use-it)
- [Common problems](#common-problems)
- [For developers](#for-developers)
- [License](#license)
- [Getting help](#getting-help)

## What is LinGallery?

LinGallery is a program that runs on your Linux desktop. It looks through your Pictures, Downloads, and Desktop folders, finds all images, and shows them to you in a clean gallery view. You can open any image in a full-screen viewer, zoom in, rotate it, crop it, or run a slideshow. Changes to your folders appear automatically.

## What it can do

- Scan your folders and organize images into albums (one album per folder)
- Browse images in a grid with thumbnail previews
- Open images in a full-screen viewer with zoom and pan using mouse or keyboard
- Rotate images left and right
- Flip images horizontally
- Crop images (overwrite the original or save a copy)
- View image details and camera settings (EXIF data)
- Rename, move, or copy images between folders
- Delete images (moves them to your system Trash with Undo)
- Run a slideshow
- Watch your folders for changes -- new images appear without restarting
- Dark and light themes

## How to use it

Double-click a thumbnail to open an image. Use arrow keys (or A/D) to navigate. Scroll to zoom. The top toolbar has zoom controls (-, 100%, +) -- click the percentage to reset to fit-to-screen. The bottom toolbar has buttons for all editing operations.

**Keyboard shortcuts**

| Key | Action |
|---|---|
| Left / A | Previous image |
| Right / D | Next image |
| Space | Start/stop slideshow |
| F | Toggle full screen |
| + / - | Zoom in / out |
| F2 | Fit image to screen |
| L | Rotate left |
| R | Rotate right |
| C | Toggle crop mode |
| I | Show image info |
| Delete | Delete current image |
| Esc | Back to gallery / cancel crop |


## For developers

See [CONTRIBUTING.md](CONTRIBUTING.md) for build instructions, project architecture, and contribution guidelines.

## License

Released under the GNU General Public License v3.0 (GPL-3.0). See the [LICENSE](LICENSE) file for details.

## Getting help

Open an issue on the [GitHub repository](https://github.com/SoufianoDev/LinGallery).
