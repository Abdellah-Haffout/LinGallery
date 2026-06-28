use std::io::{self, Write};
use std::sync::atomic::{AtomicI32, Ordering};
use lingallery_native::{
    scan_local_path, detect_connected_devices, get_device_storages,
    mtp_scan_device, mtp_scan_device_auto, MtpScanCallback
};

struct ConsoleMtpScanCallback {
    scanned_folders: AtomicI32,
    image_folders_found: AtomicI32,
    total_images_found: AtomicI32,
}

impl ConsoleMtpScanCallback {
    fn new() -> Self {
        Self {
            scanned_folders: AtomicI32::new(0),
            image_folders_found: AtomicI32::new(0),
            total_images_found: AtomicI32::new(0),
        }
    }

    fn print_summary(&self) {
        let scanned = self.scanned_folders.load(Ordering::Relaxed);
        let img_folders = self.image_folders_found.load(Ordering::Relaxed);
        let total_imgs = self.total_images_found.load(Ordering::Relaxed);

        println!("\n\x1b[1;36m==================================================\x1b[0m");
        println!("\x1b[1;32m                SCAN SUMMARY (MTP)                \x1b[0m");
        println!("\x1b[1;36m==================================================\x1b[0m");
        println!("\x1b[1;34m  📂 Total Folders Scanned:   \x1b[1;33m{}\x1b[0m", scanned);
        println!("\x1b[1;36m  📁 Image Folders Found:    \x1b[1;32m{}\x1b[0m", img_folders);
        println!("\x1b[1;35m  🖼️ Total Images Discovered: \x1b[1;32m{}\x1b[0m", total_imgs);
        println!("\x1b[1;36m==================================================\x1b[0m\n");
    }
}

impl MtpScanCallback for ConsoleMtpScanCallback {
    fn on_progress(
        &self,
        scanned_folders: i32,
        total_images: i32,
        current_folder: &str,
        top_folder_idx: i32,
        total_top_folders: i32,
    ) {
        self.scanned_folders.store(scanned_folders, Ordering::Relaxed);
        
        print!(
            "\r\x1b[2K\x1b[1;34m[SCANNING]\x1b[0m Folders: \x1b[1;33m{}\x1b[0m | Images: \x1b[1;32m{}\x1b[0m | Current: \x1b[90m{}\x1b[0m ({}/{})",
            scanned_folders,
            total_images,
            current_folder,
            top_folder_idx + 1,
            total_top_folders
        );
        let _ = io::stdout().flush();
    }

    fn on_album_found(&self, album_path: &str, album_name: &str, images_data: &str) {
        let count = images_data.lines().count() as i32;
        self.image_folders_found.fetch_add(1, Ordering::Relaxed);
        self.total_images_found.fetch_add(count, Ordering::Relaxed);

        println!(
            "\n\x1b[1;32m[ALBUM FOUND]\x1b[0m Path: '\x1b[1;34m{}\x1b[0m' | Name: '\x1b[1;35m{}\x1b[0m'",
            album_path, album_name
        );
        println!("  -> Found \x1b[1;32m{}\x1b[0m images in this folder", count);
        for (i, line) in images_data.lines().take(3).enumerate() {
            if let Some(parts) = line.split('\t').next() {
                println!("     [{}] \x1b[90m{}\x1b[0m", i + 1, parts);
            }
        }
        if count > 3 {
            println!("     ... and \x1b[1;33m{}\x1b[0m more", count - 3);
        }
        println!();
    }
}

#[tokio::main]
async fn main() {
    let args: Vec<String> = std::env::args().collect();

    if args.len() > 1 {
        match args[1].as_str() {
            "local" => {
                if args.len() < 3 {
                    eprintln!("Error: Missing local path to scan.");
                    eprintln!("Usage: cargo run -- local <path>");
                    return;
                }
                let path = &args[2];
                println!("Scanning local path: {}", path);
                let results = scan_local_path(path);
                
                let mut total_imgs = 0;
                let mut unique_folders = std::collections::HashSet::new();

                for line in results.lines() {
                    if line.is_empty() {
                        continue;
                    }
                    total_imgs += 1;
                    if let Some(parts) = line.split('\t').next() {
                        if let Some(parent) = std::path::Path::new(parts).parent() {
                            unique_folders.insert(parent.to_string_lossy().to_string());
                        }
                    }
                }
                let folder_count = unique_folders.len();

                println!("\n\x1b[1;36m==================================================\x1b[0m");
                println!("\x1b[1;32m               SCAN SUMMARY (LOCAL)               \x1b[0m");
                println!("\x1b[1;36m==================================================\x1b[0m");
                println!("\x1b[1;34m  📂 Total Image Folders:     \x1b[1;33m{}\x1b[0m", folder_count);
                println!("\x1b[1;35m  🖼️ Total Images Discovered: \x1b[1;32m{}\x1b[0m", total_imgs);
                println!("\x1b[1;36m==================================================\x1b[0m\n");

                for (i, line) in results.lines().take(10).enumerate() {
                    println!("  [{}] {}", i + 1, line);
                }
                if total_imgs > 10 {
                    println!("  ... and {} more files", total_imgs - 10);
                }
            }
            "mtp-detect" => {
                println!("Detecting connected MTP devices...");
                match detect_connected_devices() {
                    Ok(devices) => {
                        if devices.is_empty() {
                            println!("No MTP devices found. Make sure your phone is connected and in MTP/File Transfer mode.");
                        } else {
                            println!("Found {} MTP devices:", devices.len());
                            for (i, dev) in devices.iter().enumerate() {
                                println!(
                                    "  [{}] Serial: {} | Manufacturer: {} | Product: {}",
                                    i + 1,
                                    dev.serial,
                                    dev.manufacturer,
                                    dev.product
                                );
                            }
                        }
                    }
                    Err(e) => {
                        eprintln!("Error detecting MTP devices: {}", e);
                    }
                }
            }
            "mtp-scan" => {
                let deep_scan = args.contains(&"--deep".to_string()) || args.contains(&"-d".to_string());
                let mut filtered_args = args.clone();
                filtered_args.retain(|arg| arg != "--deep" && arg != "-d");

                if filtered_args.len() < 3 {
                    eprintln!("Error: Missing arguments for MTP scan.");
                    eprintln!("Usage: cargo run -- mtp-scan <serial> [storage_id] [model_name] [--deep]");
                    return;
                }
                let serial = &filtered_args[2];
                let callback = ConsoleMtpScanCallback::new();

                if filtered_args.len() < 4 {
                    println!(
                        "No storage ID specified. Automatically scanning all storages on device (Serial: {}, Deep Scan: {})...",
                        serial, deep_scan
                    );
                    match mtp_scan_device_auto(serial, deep_scan, &callback).await {
                        Ok(_) => {
                            println!("\nAutomatic MTP scanning completed successfully!");
                            callback.print_summary();
                        }
                        Err(e) => eprintln!("\nError scanning MTP device: {}", e),
                    }
                } else {
                    let storage_id: u32 = match filtered_args[3].parse() {
                        Ok(id) => id,
                        Err(_) => {
                            eprintln!("Error: Invalid storage ID. Must be an integer.");
                            return;
                        }
                    };
                    let model = if filtered_args.len() >= 5 { &filtered_args[4] } else { "MTP Device" };

                    println!(
                        "Scanning MTP Device (Serial: {}, Storage: {}, Model: {}, Deep Scan: {})...",
                        serial, storage_id, model, deep_scan
                    );
                    match mtp_scan_device(serial, storage_id, model, deep_scan, &callback).await {
                        Ok(_) => {
                            println!("\nMTP scanning completed successfully!");
                            callback.print_summary();
                        }
                        Err(e) => eprintln!("\nError scanning MTP device: {}", e),
                    }
                }
            }
            _ => {
                print_help();
            }
        }
    } else {
        run_interactive().await;
    }
}

fn print_help() {
    println!("LinGallery Native CLI");
    println!("=====================");
    println!("Usage:");
    println!("  cargo run                      - Run interactive mode");
    println!("  cargo run -- local <path>      - Scan local path for images");
    println!("  cargo run -- mtp-detect        - Detect and list connected MTP devices");
    println!("  cargo run -- mtp-scan <serial> [storage_id] [model]
                                 - Scan MTP device (scans all storages if ID omitted)");
}

async fn run_interactive() {
    println!("==========================================");
    println!("   LinGallery Native CLI (Standalone Mode) ");
    println!("==========================================");

    loop {
        println!("\nSelect an action:");
        println!("1. List & Scan local directories");
        println!("2. Detect connected MTP devices");
        println!("3. Get MTP device storages");
        println!("4. Scan MTP device (Progressive)");
        println!("5. Exit");
        print!("Enter choice (1-5): ");
        io::stdout().flush().unwrap();

        let mut choice = String::new();
        io::stdin().read_line(&mut choice).unwrap();
        let choice = choice.trim();

        match choice {
            "1" => {
                print!("Enter local directory path to scan (e.g., ~/Pictures): ");
                io::stdout().flush().unwrap();
                let mut path = String::new();
                io::stdin().read_line(&mut path).unwrap();
                let path = path.trim();
                if path.is_empty() {
                    continue;
                }
                println!("Scanning...");
                let results = scan_local_path(path);
                
                let mut total_imgs = 0;
                let mut unique_folders = std::collections::HashSet::new();

                for line in results.lines() {
                    if line.is_empty() {
                        continue;
                    }
                    total_imgs += 1;
                    if let Some(parts) = line.split('\t').next() {
                        if let Some(parent) = std::path::Path::new(parts).parent() {
                            unique_folders.insert(parent.to_string_lossy().to_string());
                        }
                    }
                }
                let folder_count = unique_folders.len();

                println!("\n\x1b[1;36m==================================================\x1b[0m");
                println!("\x1b[1;32m               SCAN SUMMARY (LOCAL)               \x1b[0m");
                println!("\x1b[1;36m==================================================\x1b[0m");
                println!("\x1b[1;34m  📂 Total Image Folders:     \x1b[1;33m{}\x1b[0m", folder_count);
                println!("\x1b[1;35m  🖼️ Total Images Discovered: \x1b[1;32m{}\x1b[0m", total_imgs);
                println!("\x1b[1;36m==================================================\x1b[0m\n");

                for (i, line) in results.lines().take(10).enumerate() {
                    println!("  [{}] {}", i + 1, line);
                }
                if total_imgs > 10 {
                    println!("  ... and {} more", total_imgs - 10);
                }
            }
            "2" => {
                println!("Detecting MTP devices...");
                match detect_connected_devices() {
                    Ok(devices) => {
                        if devices.is_empty() {
                            println!("No MTP devices found. Ensure device is in file-transfer mode.");
                        } else {
                            for (i, dev) in devices.iter().enumerate() {
                                println!(
                                    "[{}] Serial: {} | Manufacturer: {} | Product: {}",
                                    i + 1, dev.serial, dev.manufacturer, dev.product
                                );
                            }
                        }
                    }
                    Err(e) => println!("Error: {}", e),
                }
            }
            "3" => {
                println!("Detecting devices first...");
                let devices = match detect_connected_devices() {
                    Ok(d) => d,
                    Err(e) => {
                        println!("Error: {}", e);
                        continue;
                    }
                };
                if devices.is_empty() {
                    println!("No devices detected.");
                    continue;
                }
                for (i, dev) in devices.iter().enumerate() {
                    println!("[{}] {}", i + 1, dev.product);
                }
                print!("Select device index: ");
                io::stdout().flush().unwrap();
                let mut idx_str = String::new();
                io::stdin().read_line(&mut idx_str).unwrap();
                let idx: usize = match idx_str.trim().parse::<usize>() {
                    Ok(n) if n > 0 && n <= devices.len() => n - 1,
                    _ => {
                        println!("Invalid selection.");
                        continue;
                    }
                };
                let serial = &devices[idx].serial;
                println!("Retrieving storages for {}...", serial);
                match get_device_storages(serial).await {
                    Ok(storages) => {
                        if storages.is_empty() {
                            println!("No storages found. Is the phone unlocked?");
                        } else {
                            for (s_id, desc) in storages {
                                println!("  Storage ID: {} | Description: {}", s_id, desc);
                            }
                        }
                    }
                    Err(e) => println!("Error: {}", e),
                }
            }
            "4" => {
                println!("Detecting devices first...");
                let devices = match detect_connected_devices() {
                    Ok(d) => d,
                    Err(e) => {
                        println!("Error: {}", e);
                        continue;
                    }
                };
                if devices.is_empty() {
                    println!("No devices detected.");
                    continue;
                }
                for (i, dev) in devices.iter().enumerate() {
                    println!("[{}] {}", i + 1, dev.product);
                }
                print!("Select device index: ");
                io::stdout().flush().unwrap();
                let mut idx_str = String::new();
                io::stdin().read_line(&mut idx_str).unwrap();
                let idx: usize = match idx_str.trim().parse::<usize>() {
                    Ok(n) if n > 0 && n <= devices.len() => n - 1,
                    _ => {
                        println!("Invalid selection.");
                        continue;
                    }
                };
                let serial = &devices[idx].serial;
                let model = &devices[idx].product;
                
                println!("Retrieving storages for {}...", serial);
                let storages = match get_device_storages(serial).await {
                    Ok(s) => s,
                    Err(e) => {
                        println!("Error: {}", e);
                        continue;
                    }
                };
                if storages.is_empty() {
                    println!("No storages found.");
                    continue;
                }
                for (i, (s_id, desc)) in storages.iter().enumerate() {
                    println!("[{}] Storage ID: {} | {}", i + 1, s_id, desc);
                }
                print!("Select storage index: ");
                io::stdout().flush().unwrap();
                let mut s_idx_str = String::new();
                io::stdin().read_line(&mut s_idx_str).unwrap();
                let s_idx: usize = match s_idx_str.trim().parse::<usize>() {
                    Ok(n) if n > 0 && n <= storages.len() => n - 1,
                    _ => {
                        println!("Invalid selection.");
                        continue;
                    }
                };
                let storage_id = storages[s_idx].0;

                print!("Do you want a deep/comprehensive scan? (y/N): ");
                io::stdout().flush().unwrap();
                let mut deep_str = String::new();
                io::stdin().read_line(&mut deep_str).unwrap();
                let deep_scan = deep_str.trim().eq_ignore_ascii_case("y") || deep_str.trim().eq_ignore_ascii_case("yes");

                println!("Starting scan...");
                let callback = ConsoleMtpScanCallback::new();
                match mtp_scan_device(serial, storage_id, model, deep_scan, &callback).await {
                    Ok(_) => {
                        println!("\nScan complete!");
                        callback.print_summary();
                    }
                    Err(e) => println!("\nScan error: {}", e),
                }
            }
            "5" => {
                println!("Goodbye!");
                break;
            }
            _ => println!("Invalid option. Please try again."),
        }
    }
}
