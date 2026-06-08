package de.quati.shome.model

import de.quati.shome.util.getObject
import de.quati.shome.util.getPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull


data class ShellyStatus(
    private val raw: JsonObject
) {
    val cover0 = raw.getObject("cover:0")
    val sys = raw.getObject("sys")

    val lastDirection = cover0?.getPrimitive("last_direction")?.content?.let {
        when (it) {
            "close" -> Direction.CLOSE
            "open" -> Direction.OPEN
            else -> null
        }
    }
    val mac = sys?.getPrimitive("mac")?.content?.let(::Mac)
    val restartRequired = sys?.getPrimitive("restart_required")?.booleanOrNull
}

/*
http://192.168.178.69/rpc/Shelly.GetStatus

{
  "ble": {

  },
  "cloud": {
    "connected": true
  },
  "cover:0": {
    "id": 0,
    "source": "switch",
    "state": "closing",
    "move_timeout": 60,
    "move_started_at": 1780582748.28,
    "apower": 0,
    "voltage": 233.7,
    "current": 0,
    "pf": 0,
    "freq": 50,
    "aenergy": {
      "total": 0,
      "by_minute": [0, 0, 0],
      "minute_ts": 1780582740
    },
    "temperature": {
      "tC": 53.1,
      "tF": 127.6
    },
    "pos_control": false,
    "last_direction": "close"
  },
  "input:0": {
    "id": 0,
    "state": false
  },
  "input:1": {
    "id": 1,
    "state": true
  },
  "mqtt": {
    "connected": false
  },
  "sys": {
    "mac": "D48AFC42C990",
    "restart_required": false,
    "time": "16:19",
    "unixtime": 1780582749,
    "uptime": 174,
    "ram_size": 245276,
    "ram_free": 132960,
    "fs_size": 458752,
    "fs_free": 122880,
    "cfg_rev": 35,
    "kvs_rev": 0,
    "schedule_rev": 9,
    "webhook_rev": 9,
    "available_updates": {
      "stable": {
        "version": "1.7.5"
      }
    },
    "reset_reason": 3
  },
  "wifi": {
    "sta_ip": "192.168.178.69",
    "status": "got ip",
    "ssid": "FRITZ!Box 7590 ZU",
    "rssi": -58
  },
  "ws": {
    "connected": false
  }
}

 */