package at.fhooe.sail.wearable_waldlaeuefer

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import at.fhooe.sail.wearable_waldlaeuefer.databinding.ActivityMainBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.OnTokenCanceledListener
import java.io.IOException
import kotlin.math.log10


const val TAG = "WaldlaeuferApp"
const val RECORD_REQUEST_CODE = 8
const val LOCATION_REQUEST_CODE = 9
const val DEFAULT_LAT = 48.367470
const val DEFAULT_LON = 14.516010

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    // audio
    private lateinit var binding: ActivityMainBinding
    private var mRecorder: MediaRecorder? = null
    private var fakeOutput = ""

    // map
    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map_fragment) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        Log.i(TAG, "On map ready")
        mMap = googleMap

        checkLocationPermission()
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

    fun checkLocationPermission() {
        Log.i(TAG, "Check location permission")
        val permission =
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)

        if (permission != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Location permission not granted")
            makeLocationPermissionRequest()
        } else {
            getCurrentLocation()
        }
    }

    fun getCurrentLocation() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        Log.i(TAG, "Get current location")

        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            object : CancellationToken() {
                override fun onCanceledRequested(p0: OnTokenCanceledListener) =
                    CancellationTokenSource().token

                override fun isCancellationRequested() = false

            })
            .addOnSuccessListener { location: Location? ->
                if (location == null) {
                    Log.i(TAG, "Getting current location not possible - use default")
                    val currentLoc = LatLng(DEFAULT_LAT, DEFAULT_LON)
                    mMap.addMarker(
                        MarkerOptions().position(currentLoc).title("Default location")
                    )
                    val cameraPosition = CameraPosition.Builder()
                        .target(currentLoc)
                        .zoom(15f).build()

                    mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
                } else {
                    val lat = location.latitude
                    val lon = location.longitude
                    Log.i(TAG, "Getting current location possible " + lat + " " + lon)

                    val currentLoc = LatLng(lat, lon)
                    mMap.addMarker(
                        MarkerOptions().position(currentLoc).title("Your current location")
                    )
                    val cameraPosition = CameraPosition.Builder()
                        .target(currentLoc)
                        .zoom(15f).build()

                    mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
                }
            }
    }

    fun zoomAndMapLocation(lat: Float, lon: Float) {

    }

    fun makeLocationPermissionRequest() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            ),
            LOCATION_REQUEST_CODE
        )
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
                    Log.i(TAG, "Audio Permission has been denied by user")
                } else {
                    Log.i(TAG, "Audio Permission has been granted by user")
                    recordSound()
                }
            }
            LOCATION_REQUEST_CODE -> {
                if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "Location Permission has been denied by user")
                } else {
                    Log.i(TAG, "Location Permission has been granted by user")
                    getCurrentLocation()
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

        Log.i(TAG, "First amplitude: " + mRecorder?.maxAmplitude) // important comment!

        Handler().postDelayed({
            var maxAmplitude = mRecorder?.maxAmplitude
            if (maxAmplitude != null && maxAmplitude != 0) {
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