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


const SUPPORTED_EXTENSIONS: &[&str] = &["png", "jpg", "jpeg", "webp", "bmp", "tiff", "tif", "svg"];

/// Well-known top-level folders on Android/phones that typically contain user images.
/// Only these folders (case-insensitive) will be scanned at the storage root level.
const ALLOWED_TOP_FOLDERS: &[&str] = &[
    "dcim", "pictures", "photos", "camera", "download", "downloads",
    "screenshots", "images", "photo", "whatsapp", "telegram",
    "instagram", "facebook", "snapchat", "viber", "signal",
];

/// Folder names that should be skipped at any depth (case-insensitive).
const BLOCKED_FOLDERS: &[&str] = &[
    "android", ".thumbnails", ".trash", "lost+found",
    "system volume information", "cache", ".cache",
    "data", ".data", "temp", ".temp", "tmp", ".tmp",
    "databases", "lib", "libs", "files", "code_cache",
    "shared_prefs", "no_backup", "app_webview",
];

/// Maximum folder depth to scan inside each top-level folder.
/// Prevents wasting time on deeply nested directories.
const MAX_SCAN_DEPTH: u32 = 5;

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

    let roots: Vec<&str> = roots_str.split(';').filter(|s| !s.is_empty()).collect();
    // Pre-allocate 1 MiB for result string to avoid repeated reallocations
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
                // Filter out hidden dirs and lost+found in-place for efficiency
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

            // Format: path\tsize\tmodified_ms\n
            results.push_str(&path_str);
            results.push('\t');
            results.push_str(&size.to_string());
            results.push('\t');
            results.push_str(&modified_ms.to_string());
            results.push('\n');
        }
    }

    match env.new_string(results) {
        Ok(js) => js.into_raw(),
        Err(_) => env
            .new_string("")
            .expect("Failed to create empty JNI string")
            .into_raw(),
    }
}

fn datetime_to_epoch_ms(dt: &DateTime) -> u64 {
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

fn get_folder_path(
    handle: ObjectHandle,
    folders: &HashMap<ObjectHandle, (ObjectHandle, String)>,
) -> String {
    if handle.0 == 0 || handle.0 == 0xFFFFFFFF {
        return String::new();
    }
    if let Some((parent, name)) = folders.get(&handle) {
        let parent_path = get_folder_path(*parent, folders);
        if parent_path.is_empty() {
            name.clone()
        } else {
            format!("{}/{}", parent_path, name)
        }
    } else {
        String::new()
    }
}

// 1. Detect connected devices (only lists serials without opening, preventing busy state locking)
#[no_mangle]
pub extern "system" fn Java_com_soufianodev_lingallery_data_NativeScanner_nativeMtpDetectConnectedDevices(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    let devices = match MtpDevice::list_devices() {
        Ok(d) => d,
        Err(e) => {
            eprintln!("[nativeMtpDetectConnectedDevices] list_devices failed: {:?}", e);
            return env.new_string("").unwrap().into_raw();
        }
    };

    let mut result = String::new();
    for dev in devices {
        let serial = dev.serial_number.clone().unwrap_or_else(|| dev.location_id.to_string());
        let manufacturer = dev.manufacturer.clone().unwrap_or_else(|| "Unknown".to_string());
        let product = dev.product.clone().unwrap_or_else(|| "MTP Device".to_string());
        result.push_str(&format!("{}\t{}\t{}\n", serial, manufacturer, product));
    }

    match env.new_string(result) {
        Ok(js) => js.into_raw(),
        Err(_) => env.new_string("").unwrap().into_raw(),
    }
}

// 1.5 Get connected device storages (opens device dynamically to query storage info)
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
        let open_dev = match MtpDevice::open_by_serial(&serial).await {
            Ok(d) => d,
            Err(e) => {
                eprintln!("[nativeMtpGetDeviceStorages] Failed to open device: {}, error: {:?}", serial, e);
                return env.new_string("").unwrap().into_raw();
            }
        };

        let mut storage_infos = Vec::new();
        if let Ok(storages) = open_dev.storages().await {
            for s in storages {
                let desc = &s.info().description;
                let desc_clean = desc.replace('\t', " ").replace('\n', " ").replace(',', " ");
                storage_infos.push(format!("{}:{}", s.id().0, desc_clean));
            }
        }
        let storages_str = storage_infos.join(",");
        match env.new_string(storages_str) {
            Ok(js) => js.into_raw(),
            Err(_) => env.new_string("").unwrap().into_raw(),
        }
    })
}

// 2. Scan MTP device recursively with folder-by-folder progressive callback
/// Returns true if a folder name should be skipped.
fn should_skip_folder(name: &str) -> bool {
    if name.starts_with('.') {
        return true;
    }
    let lower = name.to_lowercase();
    BLOCKED_FOLDERS.contains(&lower.as_str())
}

async fn scan_folder_recursive(
    storage: &mtp_rs::mtp::Storage,
    root_folder_handle: ObjectHandle,
    root_folder_name: &str,
    serial: &str,
    storage_id_val: u32,
    model: &str,
    jvm: &jni::JavaVM,
    callback_global: &jni::objects::GlobalRef,
    scanned_folders: &mut i32,
    total_images_found: &mut i32,
    folders: &mut HashMap<ObjectHandle, (ObjectHandle, String)>,
    top_folder_idx: i32,
    total_top_folders: i32,
) {
    // Each entry: (handle, path, depth)
    let mut folders_to_visit: Vec<(ObjectHandle, String, u32)> = vec![(root_folder_handle, root_folder_name.to_string(), 1)];

    while let Some((current_handle, folder_path, depth)) = folders_to_visit.pop() {
        let objects = match storage.list_objects(Some(current_handle)).await {
            Ok(objs) => objs,
            Err(_) => continue,
        };

        *scanned_folders += 1;

        if let Ok(mut attached_env) = jvm.attach_current_thread() {
            if let Ok(path_jstr) = attached_env.new_string(&folder_path) {
                let _ = attached_env.call_method(
                    callback_global.as_obj(),
                    "onProgress",
                    "(IILjava/lang/String;II)V",
                    &[
                        jni::objects::JValue::Int(*scanned_folders),
                        jni::objects::JValue::Int(*total_images_found),
                        jni::objects::JValue::Object(path_jstr.as_ref()),
                        jni::objects::JValue::Int(top_folder_idx),
                        jni::objects::JValue::Int(total_top_folders),
                    ],
                );
            }
        }

        let mut folder_images = Vec::new();

        for obj in objects {
            if obj.is_folder() {
                // Skip if we've reached max depth
                if depth >= MAX_SCAN_DEPTH {
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

            if let Ok(mut attached_env) = jvm.attach_current_thread() {
                if let (Ok(album_path_jstr), Ok(album_name_jstr), Ok(images_data_jstr)) = (
                    attached_env.new_string(&album_path),
                    attached_env.new_string(&album_name),
                    attached_env.new_string(&images_data),
                ) {
                    let _ = attached_env.call_method(
                        callback_global.as_obj(),
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
}

#[no_mangle]
pub extern "system" fn Java_com_soufianodev_lingallery_data_NativeScanner_nativeMtpScanDevice(
    mut env: JNIEnv,
    _class: JClass,
    serial_jstr: JString,
    storage_id_jint: jint,
    model_jstr: JString,
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
        let open_dev = match MtpDevice::open_by_serial(&serial).await {
            Ok(d) => d,
            Err(e) => {
                eprintln!("[nativeMtpScanDevice] Failed to open device: {}, error: {:?}", serial, e);
                return;
            }
        };

        let storage = match open_dev.storage(StorageId(storage_id_val)).await {
            Ok(s) => s,
            Err(_) => return,
        };

        let root_objects = match storage.list_objects(None).await {
            Ok(objs) => objs,
            Err(_) => return,
        };

        let mut folders = HashMap::new();
        let mut top_level_folders = Vec::new();
        let mut root_images = Vec::new();

        for obj in root_objects {
            if obj.is_folder() {
                let name = &obj.filename;
                // At root level, only allow well-known image folders
                if name.starts_with('.') {
                    continue;
                }
                let name_lower = name.to_lowercase();
                if !ALLOWED_TOP_FOLDERS.contains(&name_lower.as_str()) {
                    continue;
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

            if let Ok(mut attached_env) = jvm.attach_current_thread() {
                if let (Ok(album_path_jstr), Ok(album_name_jstr), Ok(images_data_jstr)) = (
                    attached_env.new_string(&album_path),
                    attached_env.new_string(&album_name),
                    attached_env.new_string(&images_data),
                ) {
                    let _ = attached_env.call_method(
                        callback_global.as_obj(),
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

        let total_top_level = top_level_folders.len() as i32;

        for (i, top_folder) in top_level_folders.into_iter().enumerate() {
            let top_folder_name = top_folder.filename.clone();
            
            if let Ok(mut attached_env) = jvm.attach_current_thread() {
                if let Ok(path_jstr) = attached_env.new_string(&top_folder_name) {
                    let _ = attached_env.call_method(
                        callback_global.as_obj(),
                        "onProgress",
                        "(IILjava/lang/String;II)V",
                        &[
                            jni::objects::JValue::Int(scanned_folders),
                            jni::objects::JValue::Int(total_images_found),
                            jni::objects::JValue::Object(path_jstr.as_ref()),
                            jni::objects::JValue::Int(i as i32),
                            jni::objects::JValue::Int(total_top_level),
                        ],
                    );
                }
            }

            scan_folder_recursive(
                &storage,
                top_folder.handle,
                &top_folder_name,
                &serial,
                storage_id_val,
                &model,
                &jvm,
                &callback_global,
                &mut scanned_folders,
                &mut total_images_found,
                &mut folders,
                i as i32,
                total_top_level,
            ).await;
        }

        if let Ok(mut attached_env) = jvm.attach_current_thread() {
            if let Ok(final_jstr) = attached_env.new_string("Complete") {
                let _ = attached_env.call_method(
                    callback_global.as_obj(),
                    "onProgress",
                    "(IILjava/lang/String;II)V",
                    &[
                        jni::objects::JValue::Int(scanned_folders),
                        jni::objects::JValue::Int(total_images_found),
                        jni::objects::JValue::Object(final_jstr.as_ref()),
                        jni::objects::JValue::Int(total_top_level),
                        jni::objects::JValue::Int(total_top_level),
                    ],
                );
            }
        }
    });
}

// 3. Download Thumbnail
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
        let open_dev = match MtpDevice::open_by_serial(&serial).await {
            Ok(d) => d,
            Err(_) => return 0,
        };

        let storage = match open_dev.storage(StorageId(storage_id_val)).await {
            Ok(s) => s,
            Err(_) => return 0,
        };

        let bytes = match storage.download_thumbnail(ObjectHandle(handle_val)).await {
            Ok(b) => b,
            Err(_) => {
                // Fallback: If no EXIF thumbnail exists (common for app assets/screenshots/small PNGs),
                // download the full file if it is small (< 1.5 MiB) to use as thumbnail.
                if let Ok(obj_info) = storage.get_object_info(ObjectHandle(handle_val)).await {
                    if obj_info.size < 1524288 {
                        if let Ok(b) = storage.download(ObjectHandle(handle_val)).await {
                            b
                        } else {
                            return 0;
                        }
                    } else {
                        return 0;
                    }
                } else {
                    return 0;
                }
            }
        };

        let path = Path::new(&cache_path);
        if let Some(parent) = path.parent() {
            if fs::create_dir_all(parent).is_err() {
                return 0;
            }
        }

        if fs::write(path, bytes).is_err() {
            0
        } else {
            1
        }
    })
}

// 4. Download Full File
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
        let open_dev = match MtpDevice::open_by_serial(&serial).await {
            Ok(d) => d,
            Err(_) => return 0,
        };

        let storage = match open_dev.storage(StorageId(storage_id_val)).await {
            Ok(s) => s,
            Err(_) => return 0,
        };

        let bytes = match storage.download(ObjectHandle(handle_val)).await {
            Ok(b) => b,
            Err(_) => return 0,
        };

        let path = Path::new(&cache_path);
        if let Some(parent) = path.parent() {
            if fs::create_dir_all(parent).is_err() {
                return 0;
            }
        }

        if fs::write(path, bytes).is_err() {
            0
        } else {
            1
        }
    })
}

