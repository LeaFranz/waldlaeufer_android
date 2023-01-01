package at.fhooe.sail.wearable_waldlaeuefer

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.PackageManagerCompat.LOG_TAG
import at.fhooe.sail.wearable_waldlaeuefer.databinding.ActivityMainBinding
import java.io.File
import java.io.IOException
import kotlin.math.log10
import kotlin.math.max


const val TAG = "WaldlaeuferApp"
const val RECORD_REQUEST_CODE = 8

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var mRecorder: MediaRecorder? = null
    private var fakeOutput = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkMicPermission()
    }

    override fun onPause() {
        super.onPause()
        stopSoundRecording()
    }

    fun checkMicPermission() {
        val permission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)

        if (permission != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "RECORD_AUDIO permission not granted")
            makePermissionRequest()
        } else {
            recordSound()
        }
    }

    fun makePermissionRequest() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            RECORD_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            RECORD_REQUEST_CODE -> {

                if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "Permission has been denied by user")
                } else {
                    Log.i(TAG, "Permission has been granted by user")
                    recordSound()
                }
            }
        }
    }

    fun recordSound() {
        if (mRecorder == null) {
            mRecorder = MediaRecorder()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            fakeOutput = "${externalCacheDir?.absolutePath}/temp.3gp"
        } else {
            fakeOutput = "/dev/null"
        }

        mRecorder?.setAudioSource(MediaRecorder.AudioSource.MIC)
        mRecorder?.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
        mRecorder?.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
        mRecorder?.setOutputFile(fakeOutput)

        try {
            mRecorder?.prepare()
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        mRecorder?.start()
        Log.i(TAG, "Start sound recording")

        Log.i(TAG, "First amplitude: "+mRecorder?.maxAmplitude)

        Handler().postDelayed({
            var maxAmplitude = mRecorder?.maxAmplitude
            if (maxAmplitude != null &&  maxAmplitude != 0) {
                var maxA = maxAmplitude!!.toFloat()
                var dec: Float = (20 * (log10(maxA))).toFloat()

                Log.i(TAG, "Max. decibel: " + dec)
            }

            stopSoundRecording()
        }, 2000)
    }

    fun stopSoundRecording() {
        if (mRecorder != null) {
            Log.i(TAG, "Stop sound recording")
            mRecorder?.stop();
            mRecorder?.release();

            mRecorder = null;
        }
    }


}