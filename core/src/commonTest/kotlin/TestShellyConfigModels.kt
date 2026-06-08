import de.quati.shome.model.Mac
import de.quati.shome.model.ShellyConfig
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class TestShellyConfigModels : TestModels() {
    @Test
    fun testShellyConfigModels() {
        val result = json.decodeFromString<ShellyConfig>(params0)
        result.cover0?.maxtimeOpenDuration shouldBe 0.5.seconds
        result.cover0?.maxtimeCloseDuration shouldBe 1.seconds
        result.cover0?.swapInputs shouldBe false
        result.cover0?.invertDirections shouldBe false
        result.sys?.device?.name shouldBe "foobar"
        result.sys?.device?.mac shouldBe Mac("D48AFC42C990")
        result.sys?.device?.profile shouldBe "cover"
    }
}


// http://192.168.178.69/rpc/Shelly.GetConfig
private const val params0 = """{
  "ble": {
    "enable": false,
    "rpc": {
      "enable": false
    }
  },
  "cloud": {
    "enable": true,
    "server": "shelly-90-eu.shelly.cloud:6022/jrpc"
  },
  "cover:0": {
    "id": 0,
    "name": null,
    "motor": {
      "idle_power_thr": 2,
      "idle_confirm_period": 0.25
    },
    "maxtime_open": 0.5,
    "maxtime_close": 1.0,
    "initial_state": "stopped",
    "invert_directions": false,
    "maintenance_mode": false,
    "in_mode": "dual",
    "in_locked": false,
    "swap_inputs": false,
    "safety_switch": {
      "enable": false,
      "direction": "both",
      "action": "stop",
      "allowed_move": null
    },
    "power_limit": 2800,
    "voltage_limit": 280,
    "undervoltage_limit": 0,
    "current_limit": 10,
    "obstruction_detection": {
      "enable": false,
      "direction": "both",
      "action": "stop",
      "power_thr": 1000,
      "holdoff": 1
    },
    "slat": {
      "enable": false,
      "open_time": 1.5,
      "close_time": 1.5,
      "step": 20,
      "retain_pos": false,
      "precise_ctl": false
    }
  },
  "input:0": {
    "id": 0,
    "name": null,
    "type": "button",
    "enable": true,
    "invert": false,
    "factory_reset": true
  },
  "input:1": {
    "id": 1,
    "name": null,
    "type": "button",
    "enable": true,
    "invert": false,
    "factory_reset": true
  },
  "mqtt": {
    "enable": false,
    "server": null,
    "client_id": "shellyplus2pm-d48afc42c990",
    "user": null,
    "ssl_ca": null,
    "topic_prefix": "shellyplus2pm-d48afc42c990",
    "rpc_ntf": true,
    "status_ntf": false,
    "use_client_cert": false,
    "enable_rpc": true,
    "enable_control": true
  },
  "sys": {
    "device": {
      "name": "foobar",
      "mac": "D48AFC42C990",
      "fw_id": "20260311-095847/1.7.5-g9979d16",
      "discoverable": true,
      "eco_mode": false,
      "profile": "cover",
      "addon_type": "sensor"
    },
    "location": {
      "tz": "Europe/Berlin",
      "lat": 49.1968,
      "lon": 8.1219
    },
    "debug": {
      "level": 2,
      "file_level": null,
      "mqtt": {
        "enable": false
      },
      "websocket": {
        "enable": false
      },
      "udp": {
        "addr": null
      }
    },
    "ui_data": {

    },
    "rpc_udp": {
      "dst_addr": null,
      "listen_port": null
    },
    "sntp": {
      "server": "time.cloudflare.com"
    },
    "cfg_rev": 43
  },
  "wifi": {
    "ap": {
      "ssid": "ShellyPlus2PM-D48AFC42C990",
      "is_open": true,
      "enable": false,
      "range_extender": {
        "enable": false
      }
    },
    "sta": {
      "ssid": "FRITZ!Box 7590 ZU",
      "is_open": false,
      "enable": true,
      "ipv4mode": "dhcp",
      "ip": null,
      "netmask": null,
      "gw": null,
      "nameserver": null
    },
    "sta1": {
      "ssid": null,
      "is_open": true,
      "enable": false,
      "ipv4mode": "dhcp",
      "ip": null,
      "netmask": null,
      "gw": null,
      "nameserver": null
    },
    "roam": {
      "rssi_thr": -80,
      "interval": 60
    }
  },
  "ws": {
    "enable": true,
    "server": "https://esp8266-server.de/alexa/webhook/",
    "ssl_ca": "ca.pem"
  }
}"""