use jni::objects::{JClass, JString};
use jni::sys::{jstring, jboolean, jint};
use jni::JNIEnv;
use jwalk::WalkDirGeneric;
use std::fs;
use std::path::Path;
use std::collections::HashMap;
use mtp_rs::ptp::{ObjectHandle, StorageId, DateTime};
use mtp_rs::mtp::MtpDevice;
use chrono::{NaiveDate, NaiveTime, NaiveDateTime};

pub const SUPPORTED_EXTENSIONS: &[&str] = &["png", "jpg", "jpeg", "webp", "bmp", "tiff", "tif", "svg"];

/// Well-known top-level folders on Android/phones that typically contain user images.
/// Only these folders (case-insensitive) will be scanned at the storage root level.
pub const ALLOWED_TOP_FOLDERS: &[&str] = &[
    "dcim", "pictures", "photos", "camera", "download", "downloads",
    "screenshots", "images", "photo", "whatsapp", "telegram",
    "instagram", "facebook", "snapchat", "viber", "signal",
];

/// Folder names that should be skipped at any depth (case-insensitive).
pub const BLOCKED_FOLDERS: &[&str] = &[
    "android", ".thumbnails", ".trash", "lost+found",
    "system volume information", "cache", ".cache",
    "data", ".data", "temp", ".temp", "tmp", ".tmp",
    "databases", "lib", "libs", "files", "code_cache",
    "shared_prefs", "no_backup", "app_webview",
];

/// Maximum folder depth to scan inside each top-level folder.
/// Prevents wasting time on deeply nested directories.
pub const MAX_SCAN_DEPTH: u32 = 5;

/// Trait to allow progressive progress and album reporting during MTP scanning,
/// making the scanning core independent of JNI / JavaVM.
pub trait MtpScanCallback: Send + Sync {
    fn on_progress(
        &self,
        scanned_folders: i32,
        total_images: i32,
        current_folder: &str,
        top_folder_idx: i32,
        total_top_folders: i32,
    );
    fn on_album_found(&self, album_path: &str, album_name: &str, images_data: &str);
}

// --- JNI Implementation of MtpScanCallback ---
struct JniMtpScanCallback {
    jvm: jni::JavaVM,
    callback_global: jni::objects::GlobalRef,
}

impl MtpScanCallback for JniMtpScanCallback {
    fn on_progress(
        &self,
        scanned_folders: i32,
        total_images: i32,
        current_folder: &str,
        top_folder_idx: i32,
        total_top_folders: i32,
    ) {
        if let Ok(mut attached_env) = self.jvm.attach_current_thread() {
            if let Ok(path_jstr) = attached_env.new_string(current_folder) {
                let _ = attached_env.call_method(
                    self.callback_global.as_obj(),
                    "onProgress",
                    "(IILjava/lang/String;II)V",
                    &[
                        jni::objects::JValue::Int(scanned_folders),
                        jni::objects::JValue::Int(total_images),
                        jni::objects::JValue::Object(path_jstr.as_ref()),
                        jni::objects::JValue::Int(top_folder_idx),
                        jni::objects::JValue::Int(total_top_folders),
                    ],
                );
            }
        }
    }

    fn on_album_found(&self, album_path: &str, album_name: &str, images_data: &str) {
        if let Ok(mut attached_env) = self.jvm.attach_current_thread() {
            if let (Ok(album_path_jstr), Ok(album_name_jstr), Ok(images_data_jstr)) = (
                attached_env.new_string(album_path),
                attached_env.new_string(album_name),
                attached_env.new_string(images_data),
            ) {
                let _ = attached_env.call_method(
                    self.callback_global.as_obj(),
                    "onAlbumFound",
                    "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V",
                    &[
                        jni::objects::JValue::Object(album_path_jstr.as_ref()),
                        jni::objects::JValue::Object(album_name_jstr.as_ref()),
                        jni::objects::JValue::Object(images_data_jstr.as_ref()),
                    ],
                );
            }
        }
    }
}

// --- Core Independent Functions ---

/// Scans local folders and formats the results as tab-separated values.
pub fn scan_local_path(roots_str: &str) -> String {
    let roots: Vec<&str> = roots_str.split(';').filter(|s| !s.is_empty()).collect();
    let mut results = String::with_capacity(1024 * 1024);

    for root_path in roots {
        let resolved_path = if root_path.starts_with('~') {
            if let Some(home) = std::env::var_os("HOME") {
                let home_path = Path::new(&home);
                home_path.join(root_path.trim_start_matches("~/"))
            } else {
                Path::new(root_path).to_path_buf()
            }
        } else {
            Path::new(root_path).to_path_buf()
        };

        if !resolved_path.exists() {
            continue;
        }

        let walker = WalkDirGeneric::<((), bool)>::new(&resolved_path)
            .skip_hidden(true)
            .process_read_dir(|_depth, _path, _read_dir_state, children| {
                children.retain(|dir_entry_result| {
                    if let Ok(dir_entry) = dir_entry_result {
                        let name = dir_entry.file_name.to_string_lossy();
                        if name == "lost+found" || name.starts_with('.') {
                            return false;
                        }
                    }
                    true
                });
            });

        for entry_result in walker {
            let entry = match entry_result {
                Ok(e) => e,
                Err(_) => continue,
            };

            if !entry.file_type.is_file() {
                continue;
            }

            let path = entry.path();

            let ext = match path.extension() {
                Some(e) => e,
                None => continue,
            };

            let ext_str = ext.to_string_lossy().to_lowercase();
            if !SUPPORTED_EXTENSIONS.contains(&ext_str.as_str()) {
                continue;
            }

            let metadata = match fs::metadata(&path) {
                Ok(m) => m,
                Err(_) => continue,
            };

            let size = metadata.len();
            let modified_ms = metadata
                .modified()
                .ok()
                .and_then(|t| t.duration_since(std::time::SystemTime::UNIX_EPOCH).ok())
                .map(|d| d.as_millis() as u64)
                .unwrap_or(0);

            let path_str = path.to_string_lossy();

            results.push_str(&path_str);
            results.push('\t');
            results.push_str(&size.to_string());
            results.push('\t');
            results.push_str(&modified_ms.to_string());
            results.push('\n');
        }
    }
    results
}

pub struct MtpDeviceInfo {
    pub serial: String,
    pub manufacturer: String,
    pub product: String,
}

/// Detects connected MTP devices (lists serials, product details without opening/locking connection).
pub fn detect_connected_devices() -> Result<Vec<MtpDeviceInfo>, String> {
    let devices = match MtpDevice::list_devices() {
        Ok(d) => d,
        Err(e) => return Err(format!("{:?}", e)),
    };

    let mut result = Vec::new();
    for dev in devices {
        let serial = dev.serial_number.clone().unwrap_or_else(|| dev.location_id.to_string());
        let manufacturer = dev.manufacturer.clone().unwrap_or_else(|| "Unknown".to_string());
        let product = dev.product.clone().unwrap_or_else(|| "MTP Device".to_string());
        result.push(MtpDeviceInfo {
            serial,
            manufacturer,
            product,
        });
    }
    Ok(result)
}

/// Retrieves connected MTP device storages (opens device dynamically to query).
pub async fn get_device_storages(serial: &str) -> Result<Vec<(u32, String)>, String> {
    let open_dev = match MtpDevice::open_by_serial(serial).await {
        Ok(d) => d,
        Err(e) => return Err(format!("Failed to open device: {}, error: {:?}", serial, e)),
    };

    let mut storage_infos = Vec::new();
    if let Ok(storages) = open_dev.storages().await {
        for s in storages {
            let desc = &s.info().description;
            let desc_clean = desc.replace('\t', " ").replace('\n', " ").replace(',', " ");
            storage_infos.push((s.id().0, desc_clean));
        }
    }
    Ok(storage_infos)
}

/// Helper to determine if a folder name should be skipped.
pub fn should_skip_folder(name: &str) -> bool {
    if name.starts_with('.') {
        return true;
    }
    let lower = name.to_lowercase();
    BLOCKED_FOLDERS.contains(&lower.as_str())
}

/// Helper to convert MTP datetime to Unix epoch milliseconds.
pub fn datetime_to_epoch_ms(dt: &DateTime) -> u64 {
    let date = match NaiveDate::from_ymd_opt(dt.year as i32, dt.month as u32, dt.day as u32) {
        Some(d) => d,
        None => return 0,
    };
    let time = match NaiveTime::from_hms_opt(dt.hour as u32, dt.minute as u32, dt.second as u32) {
        Some(t) => t,
        None => return 0,
    };
    let ndt = NaiveDateTime::new(date, time);
    ndt.and_utc().timestamp_millis() as u64
}

/// Recursively scans a specific folder on an MTP storage.
pub async fn scan_folder_recursive(
    storage: &mtp_rs::mtp::Storage,
    root_folder_handle: ObjectHandle,
    root_folder_name: &str,
    serial: &str,
    storage_id_val: u32,
    model: &str,
    callback: &dyn MtpScanCallback,
    scanned_folders: &mut i32,
    total_images_found: &mut i32,
    folders: &mut HashMap<ObjectHandle, (ObjectHandle, String)>,
    top_folder_idx: i32,
    total_top_folders: i32,
    max_depth: u32,
) {
    let mut folders_to_visit: Vec<(ObjectHandle, String, u32)> = vec![(root_folder_handle, root_folder_name.to_string(), 1)];

    while let Some((current_handle, folder_path, depth)) = folders_to_visit.pop() {
        let objects = match storage.list_objects(Some(current_handle)).await {
            Ok(objs) => objs,
            Err(_) => continue,
        };

        *scanned_folders += 1;

        callback.on_progress(*scanned_folders, *total_images_found, &folder_path, top_folder_idx, total_top_folders);

        let mut folder_images = Vec::new();

        for obj in objects {
            if obj.is_folder() {
                if depth >= max_depth {
                    continue;
                }
                let name = &obj.filename;
                if should_skip_folder(name) {
                    continue;
                }
                let sub_path = format!("{}/{}", folder_path, name);
                folders.insert(obj.handle, (obj.parent, name.clone()));
                folders_to_visit.push((obj.handle, sub_path, depth + 1));
            } else if obj.is_file() {
                let filename = &obj.filename;
                let ext = match Path::new(filename).extension() {
                    Some(e) => e.to_string_lossy().to_lowercase(),
                    None => continue,
                };
                if !SUPPORTED_EXTENSIONS.contains(&ext.as_str()) {
                    continue;
                }
                folder_images.push(obj);
            }
        }

        if !folder_images.is_empty() {
            *total_images_found += folder_images.len() as i32;

            let album_path = format!("/mtp/{}/{}/{}", serial, storage_id_val, folder_path);
            let album_name = format!("{} - {}", model, folder_path);

            let mut images_data = String::new();
            for img in folder_images {
                let modified_ms = img.modified.as_ref().map(datetime_to_epoch_ms).unwrap_or(0);
                let virtual_path = format!(
                    "/mtp/{}/{}/{}/{}_{}",
                    serial, storage_id_val, folder_path, img.handle.0, img.filename
                );
                images_data.push_str(&format!(
                    "{}\t{}\t{}\n",
                    virtual_path, img.size, modified_ms
                ));
            }

            callback.on_album_found(&album_path, &album_name, &images_data);
        }
    }
}

/// Recursively scans an MTP device and fires progressive callbacks.
pub async fn mtp_scan_device(
    serial: &str,
    storage_id_val: u32,
    model: &str,
    deep_scan: bool,
    callback: &dyn MtpScanCallback,
) -> Result<(), String> {
    let open_dev = match MtpDevice::open_by_serial(serial).await {
        Ok(d) => d,
        Err(e) => {
            return Err(format!("Failed to open device: {}, error: {:?}", serial, e));
        }
    };

    let storage = match open_dev.storage(StorageId(storage_id_val)).await {
        Ok(s) => s,
        Err(e) => return Err(format!("Failed to open storage {}: {:?}", storage_id_val, e)),
    };

    let root_objects = match storage.list_objects(None).await {
        Ok(objs) => objs,
        Err(e) => return Err(format!("Failed to list root objects: {:?}", e)),
    };

    let mut folders = HashMap::new();
    let mut top_level_folders = Vec::new();
    let mut root_images = Vec::new();

    for obj in root_objects {
        if obj.is_folder() {
            let name = &obj.filename;
            if name.starts_with('.') {
                continue;
            }
            if !deep_scan {
                let name_lower = name.to_lowercase();
                if !ALLOWED_TOP_FOLDERS.contains(&name_lower.as_str()) {
                    continue;
                }
            }
            folders.insert(obj.handle, (obj.parent, name.clone()));
            top_level_folders.push(obj);
        } else if obj.is_file() {
            let filename = &obj.filename;
            let ext = match Path::new(filename).extension() {
                Some(e) => e.to_string_lossy().to_lowercase(),
                None => continue,
            };
            if SUPPORTED_EXTENSIONS.contains(&ext.as_str()) {
                root_images.push(obj);
            }
        }
    }

    let mut scanned_folders = 0;
    let mut total_images_found = 0;

    if !root_images.is_empty() {
        total_images_found += root_images.len() as i32;

        let album_path = format!("/mtp/{}/{}", serial, storage_id_val);
        let album_name = format!("{} - Root", model);

        let mut images_data = String::new();
        for img in root_images {
            let modified_ms = img.modified.as_ref().map(datetime_to_epoch_ms).unwrap_or(0);
            let virtual_path = format!(
                "/mtp/{}/{}/{}_{}",
                serial, storage_id_val, img.handle.0, img.filename
            );
            images_data.push_str(&format!(
                "{}\t{}\t{}\n",
                virtual_path, img.size, modified_ms
            ));
        }

        callback.on_album_found(&album_path, &album_name, &images_data);
    }

    let total_top_level = top_level_folders.len() as i32;

    for (i, top_folder) in top_level_folders.into_iter().enumerate() {
        let top_folder_name = top_folder.filename.clone();

        callback.on_progress(scanned_folders, total_images_found, &top_folder_name, i as i32, total_top_level);

        let max_depth = if deep_scan { 99 } else { MAX_SCAN_DEPTH };
        scan_folder_recursive(
            &storage,
            top_folder.handle,
            &top_folder_name,
            serial,
            storage_id_val,
            model,
            callback,
            &mut scanned_folders,
            &mut total_images_found,
            &mut folders,
            i as i32,
            total_top_level,
            max_depth,
        ).await;
    }

    callback.on_progress(scanned_folders, total_images_found, "Complete", total_top_level, total_top_level);

    Ok(())
}

/// Core function to download MTP thumbnail with size-fallback.
pub async fn download_mtp_thumbnail(
    serial: &str,
    storage_id: u32,
    handle: u32,
    cache_path: &str,
) -> Result<(), String> {
    let open_dev = match MtpDevice::open_by_serial(serial).await {
        Ok(d) => d,
        Err(e) => return Err(format!("Failed to open device: {:?}", e)),
    };

    let storage = match open_dev.storage(StorageId(storage_id)).await {
        Ok(s) => s,
        Err(e) => return Err(format!("Failed to open storage: {:?}", e)),
    };

    let bytes = match storage.download_thumbnail(ObjectHandle(handle)).await {
        Ok(b) => b,
        Err(_) => {
            if let Ok(obj_info) = storage.get_object_info(ObjectHandle(handle)).await {
                if obj_info.size < 1524288 {
                    if let Ok(b) = storage.download(ObjectHandle(handle)).await {
                        b
                    } else {
                        return Err("Failed to download fallback".to_string());
                    }
                } else {
                    return Err("File too large for fallback thumbnail".to_string());
                }
            } else {
                return Err("Failed to get object info for fallback".to_string());
            }
        }
    };

    let path = Path::new(cache_path);
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent).map_err(|e| format!("Failed to create dirs: {:?}", e))?;
    }

    fs::write(path, bytes).map_err(|e| format!("Failed to write file: {:?}", e))
}

/// Core function to download MTP full file.
pub async fn download_mtp_file(
    serial: &str,
    storage_id: u32,
    handle: u32,
    cache_path: &str,
) -> Result<(), String> {
    let open_dev = match MtpDevice::open_by_serial(serial).await {
        Ok(d) => d,
        Err(e) => return Err(format!("Failed to open device: {:?}", e)),
    };

    let storage = match open_dev.storage(StorageId(storage_id)).await {
        Ok(s) => s,
        Err(e) => return Err(format!("Failed to open storage: {:?}", e)),
    };

    let bytes = storage.download(ObjectHandle(handle)).await
        .map_err(|e| format!("Download failed: {:?}", e))?;

    let path = Path::new(cache_path);
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent).map_err(|e| format!("Failed to create dirs: {:?}", e))?;
    }

    fs::write(path, bytes).map_err(|e| format!("Failed to write file: {:?}", e))
}

/// Scans all storages of a device automatically based on serial, querying storages and model info.
pub async fn mtp_scan_device_auto(
    serial: &str,
    deep_scan: bool,
    callback: &dyn MtpScanCallback,
) -> Result<(), String> {
    // 1. Find model name from connected devices
    let model = match detect_connected_devices() {
        Ok(devices) => {
            devices.into_iter()
                .find(|d| d.serial == serial)
                .map(|d| d.product)
                .unwrap_or_else(|| "MTP Device".to_string())
        }
        Err(_) => "MTP Device".to_string(),
    };

    // 2. Retrieve storages
    let storages = get_device_storages(serial).await?;
    if storages.is_empty() {
        return Err("No storages found on device. Is it unlocked?".to_string());
    }

    // 3. Scan each storage
    for (storage_id, _) in storages {
        let _ = mtp_scan_device(serial, storage_id, &model, deep_scan, callback).await?;
    }

    Ok(())
}

// --- JNI Bindings (wrapping core functions) ---

#[no_mangle]
pub extern "system" fn Java_com_soufianodev_lingallery_data_NativeScanner_nativeScan(
    mut env: JNIEnv,
    _class: JClass,
    roots_jstr: JString,
) -> jstring {
    let roots_str: String = match env.get_string(&roots_jstr) {
        Ok(s) => s.into(),
        Err(_) => {
            return env
                .new_string("")
                .expect("Failed to create empty JNI string")
                .into_raw();
        }
    };

    let results = scan_local_path(&roots_str);

    match env.new_string(results) {
        Ok(js) => js.into_raw(),
        Err(_) => env
            .new_string("")
            .expect("Failed to create empty JNI string")
            .into_raw(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_soufianodev_lingallery_data_NativeScanner_nativeMtpDetectConnectedDevices(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    let mut result = String::new();
    if let Ok(devices) = detect_connected_devices() {
        for dev in devices {
            result.push_str(&format!("{}\t{}\t{}\n", dev.serial, dev.manufacturer, dev.product));
        }
    }

    match env.new_string(result) {
        Ok(js) => js.into_raw(),
        Err(_) => env.new_string("").unwrap().into_raw(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_soufianodev_lingallery_data_NativeScanner_nativeMtpGetDeviceStorages(
    mut env: JNIEnv,
    _class: JClass,
    serial_jstr: JString,
) -> jstring {
    let serial: String = match env.get_string(&serial_jstr) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("").unwrap().into_raw(),
    };

    let rt = match tokio::runtime::Runtime::new() {
        Ok(r) => r,
        Err(_) => return env.new_string("").unwrap().into_raw(),
    };

    rt.block_on(async {
        let storages_str = match get_device_storages(&serial).await {
            Ok(storages) => {
                let info_strs: Vec<String> = storages.into_iter()
                    .map(|(id, desc)| format!("{}:{}", id, desc))
                    .collect();
                info_strs.join(",")
            }
            Err(_) => String::new(),
        };

        match env.new_string(storages_str) {
            Ok(js) => js.into_raw(),
            Err(_) => env.new_string("").unwrap().into_raw(),
        }
    })
}

#[no_mangle]
pub extern "system" fn Java_com_soufianodev_lingallery_data_NativeScanner_nativeMtpScanDevice(
    mut env: JNIEnv,
    _class: JClass,
    serial_jstr: JString,
    storage_id_jint: jint,
    model_jstr: JString,
    deep_scan_jboolean: jboolean,
    callback: jni::objects::JObject,
) {
    let serial: String = match env.get_string(&serial_jstr) {
        Ok(s) => s.into(),
        Err(_) => return,
    };
    let model: String = match env.get_string(&model_jstr) {
        Ok(s) => s.into(),
        Err(_) => "MTP Device".to_string(),
    };

    let storage_id_val = storage_id_jint as u32;
    let deep_scan = deep_scan_jboolean != 0;

    let callback_global = match env.new_global_ref(callback) {
        Ok(gr) => gr,
        Err(_) => return,
    };
    let jvm = match env.get_java_vm() {
        Ok(j) => j,
        Err(_) => return,
    };

    let rt = match tokio::runtime::Runtime::new() {
        Ok(r) => r,
        Err(_) => return,
    };

    rt.block_on(async {
        let jni_callback = JniMtpScanCallback {
            jvm,
            callback_global,
        };

        let _ = mtp_scan_device(&serial, storage_id_val, &model, deep_scan, &jni_callback).await;
    });
}

#[no_mangle]
pub extern "system" fn Java_com_soufianodev_lingallery_data_NativeScanner_nativeMtpDownloadThumbnail(
    mut env: JNIEnv,
    _class: JClass,
    serial_jstr: JString,
    storage_id_jint: jint,
    handle_jint: jint,
    cache_path_jstr: JString,
) -> jboolean {
    let serial: String = match env.get_string(&serial_jstr) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };
    let cache_path: String = match env.get_string(&cache_path_jstr) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };

    let storage_id_val = storage_id_jint as u32;
    let handle_val = handle_jint as u32;

    let rt = match tokio::runtime::Runtime::new() {
        Ok(r) => r,
        Err(_) => return 0,
    };

    rt.block_on(async {
        match download_mtp_thumbnail(&serial, storage_id_val, handle_val, &cache_path).await {
            Ok(_) => 1,
            Err(_) => 0,
        }
    })
}

#[no_mangle]
pub extern "system" fn Java_com_soufianodev_lingallery_data_NativeScanner_nativeMtpDownloadFile(
    mut env: JNIEnv,
    _class: JClass,
    serial_jstr: JString,
    storage_id_jint: jint,
    handle_jint: jint,
    cache_path_jstr: JString,
) -> jboolean {
    let serial: String = match env.get_string(&serial_jstr) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };
    let cache_path: String = match env.get_string(&cache_path_jstr) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };

    let storage_id_val = storage_id_jint as u32;
    let handle_val = handle_jint as u32;

    let rt = match tokio::runtime::Runtime::new() {
        Ok(r) => r,
        Err(_) => return 0,
    };

    rt.block_on(async {
        match download_mtp_file(&serial, storage_id_val, handle_val, &cache_path).await {
            Ok(_) => 1,
            Err(_) => 0,
        }
    })
}
