package com.kerneldroid.karchiver.data.storage

import org.junit.Assert.assertEquals
import org.junit.Test

class VolumeKindTest {

    @Test
    fun primaryIsInternalEvenIfRemovableFlagOdd() {
        assertEquals(
            VolumeKind.INTERNAL,
            classifyVolume(
                isPrimary = true,
                isEmulated = false,
                isRemovable = true,
                usbMassStorageAttached = false
            )
        )
        assertEquals(
            VolumeKind.INTERNAL,
            classifyVolume(
                isPrimary = true,
                isEmulated = false,
                isRemovable = true,
                usbMassStorageAttached = true
            )
        )
        assertEquals(
            VolumeKind.INTERNAL,
            classifyVolume(
                isPrimary = true,
                isEmulated = false,
                isRemovable = false,
                usbMassStorageAttached = true
            )
        )
    }

    @Test
    fun emulatedIsInternal() {
        assertEquals(
            VolumeKind.INTERNAL,
            classifyVolume(
                isPrimary = false,
                isEmulated = true,
                isRemovable = false,
                usbMassStorageAttached = false
            )
        )
        assertEquals(
            VolumeKind.INTERNAL,
            classifyVolume(
                isPrimary = false,
                isEmulated = true,
                isRemovable = true,
                usbMassStorageAttached = false
            )
        )
        assertEquals(
            VolumeKind.INTERNAL,
            classifyVolume(
                isPrimary = false,
                isEmulated = true,
                isRemovable = true,
                usbMassStorageAttached = true
            )
        )
    }

    @Test
    fun removableWithUsbIsUsb() {
        assertEquals(
            VolumeKind.USB,
            classifyVolume(
                isPrimary = false,
                isEmulated = false,
                isRemovable = true,
                usbMassStorageAttached = true
            )
        )
    }

    @Test
    fun removableWithoutUsbIsSdCard() {
        assertEquals(
            VolumeKind.SD_CARD,
            classifyVolume(
                isPrimary = false,
                isEmulated = false,
                isRemovable = true,
                usbMassStorageAttached = false
            )
        )
    }

    @Test
    fun nonRemovableNonPrimaryIsInternal() {
        assertEquals(
            VolumeKind.INTERNAL,
            classifyVolume(
                isPrimary = false,
                isEmulated = false,
                isRemovable = false,
                usbMassStorageAttached = false
            )
        )
        assertEquals(
            VolumeKind.INTERNAL,
            classifyVolume(
                isPrimary = false,
                isEmulated = false,
                isRemovable = false,
                usbMassStorageAttached = true
            )
        )
    }
}
