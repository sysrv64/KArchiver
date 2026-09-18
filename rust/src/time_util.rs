pub(crate) fn days_from_civil(year: i64, month: u32, day: u32) -> i64 {
    let y = if month <= 2 { year - 1 } else { year };
    let era = if y >= 0 { y } else { y - 399 } / 400;
    let yoe = (y - era * 400) as u64;
    let mp = if month > 2 { month - 3 } else { month + 9 } as u64;
    let doy = (153 * mp + 2) / 5 + day as u64 - 1;
    let doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
    era * 146_097 + doe as i64 - 719_468
}

pub(crate) fn civil_from_days(days: i64) -> (i64, u32, u32) {
    let z = days + 719_468;
    let era = if z >= 0 { z } else { z - 146_096 } / 146_097;
    let doe = (z - era * 146_097) as u64;
    let yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365;
    let y = yoe as i64 + era * 400;
    let doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
    let mp = (5 * doy + 2) / 153;
    let day = (doy - (153 * mp + 2) / 5 + 1) as u32;
    let month = if mp < 10 { mp + 3 } else { mp - 9 } as u32;
    let year = if month <= 2 { y + 1 } else { y };
    (year, month, day)
}

pub(crate) fn dos_to_unix_millis(file_time: u32) -> u64 {
    let date = file_time >> 16;
    let time = file_time & 0xFFFF;
    let year = 1980 + ((date >> 9) & 0x7F) as i64;
    let month = (date >> 5) & 0x0F;
    let day = date & 0x1F;
    if month == 0 || day == 0 {
        return 0;
    }
    let hour = (time >> 11) & 0x1F;
    let minute = (time >> 5) & 0x3F;
    let second = (time & 0x1F) * 2;
    let days = days_from_civil(year, month, day);
    if days < 0 {
        return 0;
    }
    days as u64 * 86_400_000
        + hour as u64 * 3_600_000
        + minute as u64 * 60_000
        + second as u64 * 1_000
}
