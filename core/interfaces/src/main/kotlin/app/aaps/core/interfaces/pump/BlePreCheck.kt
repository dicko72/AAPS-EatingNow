package app.aaps.core.interfaces.pump

import androidx.appcompat.app.AppCompatActivity

interface BlePreCheck {

    fun prerequisitesCheck(activity: AppCompatActivity): Boolean
}