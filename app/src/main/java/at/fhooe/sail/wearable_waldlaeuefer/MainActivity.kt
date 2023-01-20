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
import android.view.WindowManager
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
    private var heartPoints: Double? = null
    private var moveMinutes: Int? = null
    lateinit var account: GoogleSignInAccount
    private var decibel: Float? = null

    // estimation
    private val emojiArray = arrayOf("😀", "🙂", "🙁", "😞")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map_fragment) as SupportMapFragment
        mapFragment.getMapAsync(this)
        binding.activityMainBtnAdd.setOnClickListener(this)
    }

    fun createFitnessAPIClient() {
        fitnessOptions = FitnessOptions.builder()
            .addDataType(DataType.TYPE_HEART_RATE_BPM, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.AGGREGATE_HEART_RATE_SUMMARY, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.TYPE_HEART_POINTS, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.TYPE_MOVE_MINUTES, FitnessOptions.ACCESS_READ)
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

    // get data for last half hour
    fun accessGoogleFit() {
        val end = LocalDateTime.now()
        val start = end.minusMinutes(30)
        val endSeconds = end.atZone(ZoneId.systemDefault()).toEpochSecond()
        val startSeconds = start.atZone(ZoneId.systemDefault()).toEpochSecond()

        setHeartRateData(startSeconds, endSeconds)
        setHeartPointData(startSeconds, endSeconds)
        setMoveMinuteData(startSeconds, endSeconds)


        Handler().postDelayed({

            Log.d(
                TAG,
                "before estimate dialog " + heartRate + " " + heartPoints + " " + moveMinutes
            )

            if (heartRate != 0.0 && heartPoints != null && moveMinutes != null) {
                estimateFeelingDialog()
            } else {
                noEstimatePossibleDialog("No data available for estimating your feeling.")
            }
        }, 1000)
    }

    fun setHeartRateData(startSeconds: Long, endSeconds: Long) {
        heartRate = 0.0
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
        heartPoints = null
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
                    } else {
                        heartPoints = 0.0
                    }
                }
            })
            .addOnFailureListener({ e ->
                Log.d(TAG, "Hear Points: OnFailure()", e)
                heartPoints = 0.0
            })
    }

    fun setMoveMinuteData(startSeconds: Long, endSeconds: Long) {
        moveMinutes = null
        val readRequest = DataReadRequest.Builder()
            .aggregate(DataType.TYPE_MOVE_MINUTES)
            .setTimeRange(startSeconds, endSeconds, TimeUnit.SECONDS)
            .bucketByTime(1, TimeUnit.HOURS)
            .build()

        Fitness.getHistoryClient(this, account)
            .readData(readRequest)
            .addOnSuccessListener({ response ->
                Log.i(TAG, "Move minutes: OnSuccess() " + response)
                var buckets = response.buckets

                Log.i(TAG, "move minutes bucekt " + buckets)

                if (buckets.size > 0 && buckets[0].dataSets.size > 0) {
                    var dataSet = buckets[0].dataSets[0]

                    if (dataSet.dataPoints.size > 0) {
                        var points =
                            dataSet.dataPoints[0].getValue(Field.FIELD_DURATION)
                        moveMinutes = points.toString().toInt()
                        Log.i(TAG, "Move minutes in the last hour: " + moveMinutes)
                    } else {
                        moveMinutes = null
                    }
                }
            })
            .addOnFailureListener({ e ->
                Log.d(TAG, "Move minutes: OnFailure()", e)
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
                noEstimatePossibleDialog("No permissions to Google Fit granted.")
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
            decibel = null
            var maxAmplitude = mRecorder?.maxAmplitude
            var message: String = ""
            if (maxAmplitude != null && maxAmplitude != 0) {
                var maxA = maxAmplitude!!.toFloat()
                decibel = (20 * (log10(maxA))).toFloat()

                Log.i(TAG, "Max. decibel: " + decibel)
                var emojiIndex = estimateNoiseFeeling()
                if (emojiIndex != -1) {
                    message =
                        "The max. noise was " + decibel + " decibel, so we estimate that you are feeling " + emojiArray[emojiIndex]
                } else {
                    message = "The max. noise was " + decibel + " decibel."
                }

            } else {
                decibel = null
                message = "Recording noise failed."
            }

            stopSoundRecording()
            binding.activityMainProgress.visibility = View.INVISIBLE
            binding.activityMainTvRecording.visibility = View.INVISIBLE

            // dialog 2
            noiseLevelDialog(message)
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
        bob.setNegativeButton("Skip") { _, _ ->
            noEstimatePossibleDialog()
        }
        val d: Dialog = bob.create()
        d.show()
    }

    // dialog 2
    fun noiseLevelDialog(message: String) {
        val bob: AlertDialog.Builder = AlertDialog.Builder(this)
        bob.setTitle("Noise in the Area")

        bob.setMessage(message)

        // only 1 button if no permission for google fit?

        bob.setPositiveButton("Next") { _, _ ->
            Log.i(TAG, "TODO: implement survey")
            createFitnessAPIClient()
        }
        bob.setNegativeButton("Cancel") { _, _ ->
            Log.d(TAG, "Cancelled dialog")
        }
        val d: Dialog = bob.create()
        d.show()
    }

    // https://developers.google.com/fit/datatypes/activity#heart_points
    /**
     * move minute > 30 && heat points > 30 -> activity
     *
     * heart points: 1 per minute low-mid intensity -> high intensity is 2 points per minute
     * one heart point for each minute of moderately intense activity
     * move minutes: minutes
     */


    fun getFeelingEstimate(): String {
        var feelingIncrease = 0
        var heartrateNormal = true
        var isActivity = false
        // higher heart rate
        if (heartRate!! > 100) {
            // no activity -> stress?
            Log.i(TAG, "minutes and points " + moveMinutes + " " + heartPoints)
            if (moveMinutes!! < 10 && heartPoints!! < 20.0) {
                // not even 10 minutes of light activity
                feelingIncrease = 1
                heartrateNormal = false
                isActivity = false
            } else {
                // some activity
                heartrateNormal = false
                isActivity = true
            }
        } else {
            // normal heartrate - noise feeling
            feelingIncrease = 0
        }

        var noiseFeelingIndex = estimateNoiseFeeling()
        var emojiIndex =
            if ((noiseFeelingIndex + feelingIncrease) < emojiArray.size) noiseFeelingIndex + feelingIncrease else noiseFeelingIndex

        return getFitFeelingText(
            heartrateNormal,
            isActivity
        ) + "\n" + "Therefore we estimate you are feeling " + emojiArray[emojiIndex]

    }

    fun getFitFeelingText(heartRateNormal: Boolean, isActivity: Boolean): String {
        if (heartRateNormal) {
            return "Your heartrate seems normal with an average of " + heartRate + " in the last half hour."
        } else {
            if (isActivity) {
                return "Your heartrate is elevated with an average of " + heartRate + " in the last half hour, but it seems like you have been active."
            } else {
                return "Your heartrate is elevated with an average of " + heartRate + " in the last half hour, but it seems like you have not been active. It might be the case that you are stressed."
            }
        }
    }

    fun estimateNoiseFeeling(): Int {
        if (decibel != null) {
            if (decibel!! < 70) {
                return 0
            } else if (decibel!! >= 70 && decibel!! < 94) {
                return 1
            } else if (decibel!! >= 94 && decibel!! < 129) {
                return 2
            }
            return 3
        }
        return -1
    }

    // dialog 3
    fun estimateFeelingDialog() {
        val bob: AlertDialog.Builder = AlertDialog.Builder(this)
        bob.setTitle("Noise in the Area")
        var message = "The max. noise was " + decibel + " decibel.\n" + getFeelingEstimate()
        bob.setMessage(
            //"Your average heartrate in the last hour was " + heartRate + " and you received " + heartPoints + " heart points."
            message
        )

        bob.setPositiveButton("Done") { _, _ ->
        }
        /*
        bob.setNegativeButton("Skip to survey") { _, _ ->
            Log.d(TAG, "TODO: implement survey")
        }

         */
        val d: Dialog = bob.create()
        d.show()
    }

    // dialog 4
    fun noEstimatePossibleDialog(message: String = "") {
        val bob: AlertDialog.Builder = AlertDialog.Builder(this)
        bob.setTitle("Noise in the Area")
        if (message.length > 0) {
            bob.setMessage(
                message
            )
        } else {
            bob.setMessage(
                "Estimating your feeling based on the noise level not possible."
            )
        }


        bob.setPositiveButton("Done") { _, _ ->
        }
        val d: Dialog = bob.create()
        d.show()
    }
}


