package at.fhooe.sail.wearable_waldlaeuefer

import android.Manifest
import android.accounts.Account
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.view.View.OnClickListener
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import at.fhooe.sail.wearable_waldlaeuefer.databinding.ActivityMainBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field
import com.google.android.gms.fitness.request.DataReadRequest
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
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.xml.datatype.DatatypeConstants.SECONDS
import kotlin.math.log10


const val TAG = "WaldlaeuferApp"
const val RECORD_REQUEST_CODE = 8
const val LOCATION_REQUEST_CODE = 9
const val DEFAULT_LAT = 48.367470
const val DEFAULT_LON = 14.516010
const val GOOGLE_FIT_PERMISSIONS_REQUEST_CODE = 10

class MainActivity : AppCompatActivity(), OnMapReadyCallback, OnClickListener {

    // audio
    private lateinit var binding: ActivityMainBinding
    private var mRecorder: MediaRecorder? = null
    private var fakeOutput = ""

    // map
    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // fitness API
    lateinit var fitnessOptions: FitnessOptions
    private var heartRate = 0.0
    private var heartPoints = 0.0
    lateinit var account: GoogleSignInAccount

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map_fragment) as SupportMapFragment
        mapFragment.getMapAsync(this)
        binding.activityMainBtnAdd.setOnClickListener(this)

        createFitnessAPIClient()
    }

    fun createFitnessAPIClient() {
        fitnessOptions = FitnessOptions.builder()
            .addDataType(DataType.TYPE_HEART_RATE_BPM, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.AGGREGATE_HEART_RATE_SUMMARY, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.TYPE_HEART_POINTS, FitnessOptions.ACCESS_READ)
            .build()

        account = GoogleSignIn.getAccountForExtension(this, fitnessOptions)

        if (!GoogleSignIn.hasPermissions(account, fitnessOptions)) {
            GoogleSignIn.requestPermissions(
                this, // your activity
                GOOGLE_FIT_PERMISSIONS_REQUEST_CODE,
                account,
                fitnessOptions
            )
        } else {
            Log.d(TAG, "Permission for google fit granted")
            accessGoogleFit()
        }
    }

    // get data for last hour
    fun accessGoogleFit() {
        val end = LocalDateTime.now()
        val start = end.minusHours(1)
        val endSeconds = end.atZone(ZoneId.systemDefault()).toEpochSecond()
        val startSeconds = start.atZone(ZoneId.systemDefault()).toEpochSecond()

        setHeartRateData(startSeconds, endSeconds)
        setHeartPointData(startSeconds, endSeconds)
    }

    fun setHeartRateData(startSeconds: Long, endSeconds: Long) {
        val readRequest = DataReadRequest.Builder()
            .aggregate(DataType.TYPE_HEART_RATE_BPM)
            .setTimeRange(startSeconds, endSeconds, TimeUnit.SECONDS)
            .bucketByTime(1, TimeUnit.HOURS)
            .build()

        Fitness.getHistoryClient(this, account)
            .readData(readRequest)
            .addOnSuccessListener({ response ->
                Log.i(TAG, "Heart rate: OnSuccess() " + response)

                var buckets = response.buckets

                if (buckets.size > 0 && buckets[0].dataSets.size > 0) {
                    var dataSet = buckets[0].dataSets[0]

                    if (dataSet.dataPoints.size > 0) {
                        var points =
                            dataSet.dataPoints[0].getValue(Field.FIELD_AVERAGE)
                        heartRate = points.toString().toDouble()
                        Log.i(TAG, "Average heart rate in the last hour: " + heartRate)
                    }
                }
            })
            .addOnFailureListener({ e ->
                Log.d(TAG, "Heart Rate:  OnFailure()", e)
                heartRate = 0.0
            })
    }

    fun setHeartPointData(startSeconds: Long, endSeconds: Long) {
        val readRequest = DataReadRequest.Builder()
            .aggregate(DataType.TYPE_HEART_POINTS)
            .setTimeRange(startSeconds, endSeconds, TimeUnit.SECONDS)
            .bucketByTime(1, TimeUnit.HOURS)
            .build()

        Fitness.getHistoryClient(this, account)
            .readData(readRequest)
            .addOnSuccessListener({ response ->
                Log.i(TAG, "Heart Points: OnSuccess() " + response)
                var buckets = response.buckets

                if (buckets.size > 0 && buckets[0].dataSets.size > 0) {
                    var dataSet = buckets[0].dataSets[0]

                    if (dataSet.dataPoints.size > 0) {
                        var points =
                            dataSet.dataPoints[0].getValue(Field.FIELD_INTENSITY)
                        heartPoints = points.toString().toDouble()
                        Log.i(TAG, "Heart points in the last hour: " + heartPoints)
                    }
                }
            })
            .addOnFailureListener({ e ->
                Log.d(TAG, "Hear Points: OnFailure()", e)
                heartPoints = 0.0
            })
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
                   zoomToDefaultLocation()
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

    fun zoomToDefaultLocation() {
        Log.i(TAG, "Getting current location not possible - use default")
        val currentLoc = LatLng(DEFAULT_LAT, DEFAULT_LON)
        mMap.addMarker(
            MarkerOptions().position(currentLoc).title("Default location")
        )
        val cameraPosition = CameraPosition.Builder()
            .target(currentLoc)
            .zoom(15f).build()

        mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (resultCode) {
            Activity.RESULT_OK -> when (requestCode) {
                GOOGLE_FIT_PERMISSIONS_REQUEST_CODE -> {
                    Log.d(TAG, "Google fit permissions grantend")
                    accessGoogleFit()
                }
            }
            else -> {
                Log.d(TAG, "Google Fit permissions not granted")
            }
        }
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
                    zoomToDefaultLocation()
                } else {
                    Log.i(TAG, "Location Permission has been granted by user")
                    getCurrentLocation()
                }
            }
            else -> {
                Log.w(TAG, "Unexpected permission request code")
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

        binding.activityMainProgress.visibility = View.VISIBLE
        binding.activityMainTvRecording.visibility = View.VISIBLE

        mRecorder?.start()
        Log.i(TAG, "Start sound recording")

        Log.i(TAG, "First amplitude: " + mRecorder?.maxAmplitude) // important comment!

        Handler().postDelayed({
            var maxAmplitude = mRecorder?.maxAmplitude
            var message: String = ""
            if (maxAmplitude != null && maxAmplitude != 0) {
                var maxA = maxAmplitude!!.toFloat()
                var dec: Float = (20 * (log10(maxA))).toFloat()

                Log.i(TAG, "Max. decibel: " + dec)
                message = "The max. noise was " + dec + " decibel."
            } else {
                message = "Recording noise failed. \nPlease use the survey."
            }

            stopSoundRecording()
            binding.activityMainProgress.visibility = View.INVISIBLE
            binding.activityMainTvRecording.visibility = View.INVISIBLE

            val bob: AlertDialog.Builder = AlertDialog.Builder(this)
            bob.setTitle("Survey")

            bob.setMessage(message)

            bob.setPositiveButton("Next") { _, _ ->
                Log.i(TAG, "TODO: implement survey")
            }
            bob.setNegativeButton("Cancel") { _, _ ->
                Log.d(TAG, "Cancelled dialog")
            }
            val d: Dialog = bob.create()
            d.show()

        }, 3000)
    }

    fun stopSoundRecording() {
        if (mRecorder != null) {
            Log.i(TAG, "Stop sound recording")
            mRecorder?.stop();
            mRecorder?.release();

            mRecorder = null;
        }
    }

    override fun onClick(_view: View?) {
        when (_view?.id) {
            R.id.activity_main_btn_add -> {
                onAddButtonClicked()
            }
            else -> {
                Log.e(TAG, "MainActivity: onClick - unexpected id")
            }
        }
    }

    // dialog 1
    fun onAddButtonClicked() {
        val bob: AlertDialog.Builder = AlertDialog.Builder(this)
        bob.setTitle("Record Noise in the Area")
        bob.setMessage(
            "Recording audio requires access to a microphone. \n" +
                    "Please don’t put your phone away for the duration of the recording. \n" +
                    "The recording will only be used to asses noise level."
        )

        bob.setPositiveButton("Record") { _, _ ->
            checkMicPermission()
        }
        bob.setNegativeButton("Skip to survey") { _, _ ->
            Log.d(TAG, "TODO: implement survey")
        }
        val d: Dialog = bob.create()
        d.show()
    }


}