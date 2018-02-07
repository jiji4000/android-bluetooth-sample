package jiji4000.jp.androidbluetoothsample

import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button

class MainActivity : AppCompatActivity() {
    lateinit var centralButton : Button
    lateinit var peripheralButton : Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // instantiate
        centralButton = findViewById(R.id.central_btn)
        centralButton.setOnClickListener {
            startActivity(Intent(this,CentralActivity::class.java))
        }
        peripheralButton = findViewById(R.id.periphera_btn)
        peripheralButton.setOnClickListener {
            startActivity(Intent(this,PeripheralActivity::class.java))
        }
    }
}
