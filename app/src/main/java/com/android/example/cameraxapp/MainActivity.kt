package com.android.example.cameraxapp

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.SensorManager.getOrientation
import android.hardware.SensorManager.getQuaternionFromVector
import android.hardware.SensorManager.getRotationMatrix
import android.location.Location
import android.location.Location.*
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.android.example.cameraxapp.databinding.ActivityMainBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest.*
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.OnTokenCanceledListener
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.io.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin


class MainActivity : AppCompatActivity(), SensorEventListener {
    // фото
    lateinit var image : ByteArray

    // ширина и высота фото в пикселях (из битмапы)
    var width : Int = 0
    var height : Int = 0

    // sight angle (объект с углоами обзора камеры)
    lateinit var sa : CamParams

    // сериализуемый объект кооридант, передаваемых с сервера
    lateinit var coords : Coords

    // широта и долгота
    var lat: Double = 0.0
    var lon: Double = 0.0

    // адрес сервера
    var url : String = "http://192.168.0.103:8080"

    var calc : Boolean = false

    var v_Img_Sun: Vector = Vector(0.0,0.0,0.0)
    var v_Eph_Sun: Vector = Vector(0.0,0.0,0.0)

    var q_ph_Sun : Quaternion = Quaternion(0.0,0.0,0.0,0.0)

    // кватернион вращения
    var q_rot : Quaternion = Quaternion(0.0,0.0,0.0,0.0)

    // кватернион разницы (доворота)
    var q_diff : Quaternion = Quaternion(0.0,0.0,0.0,0.0)

    val v_frontFRD = Vector(1.0, 0.0, 0.0)
    var v_frontNED = Vector(0.0, 0.0, 0.0)

    private lateinit var viewBinding: ActivityMainBinding

    private var imageCapture: ImageCapture? = null

    private lateinit var cameraExecutor: ExecutorService

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var mSensorManager: SensorManager
    lateinit var accelerometer: Sensor
    lateinit var magnetometer: Sensor
    lateinit var rot: Sensor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }

        sa = CamParams(application)
        sa.calculateFOV()

        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        // Set up the listeners for take photo and video capture buttons
        viewBinding.imageCaptureButton.setOnClickListener { takePhoto() }

        cameraExecutor = Executors.newSingleThreadExecutor()

        // location
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        //sensor
        mSensorManager = getSystemService(SENSOR_SERVICE) as SensorManager;
        accelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)!!
        magnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)!!
        rot = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)!!

        /*mSensorManager.registerListener(this, accelerometer, SENSOR_DELAY_UI)
        mSensorManager.registerListener(this, magnetometer, SENSOR_DELAY_UI)*/

        // костыль (1-й раз локация не работает))))
        get_Location()
    }

    override fun onResume() {
        super.onResume()
        try {
            mSensorManager.registerListener(this, accelerometer,SensorManager.SENSOR_DELAY_NORMAL)
            mSensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL)
            mSensorManager.registerListener(this, rot, SensorManager.SENSOR_DELAY_NORMAL)
        } catch (_: Exception) { }
    }

    override fun onPause() {
       super.onPause()
        mSensorManager.unregisterListener(this)
    }

    private fun get_Image() {
        val bitmap = viewBinding.viewFinder.bitmap
        width = bitmap?.width ?: return
        height = bitmap.height
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        image = stream.toByteArray()
    }

    private fun get_Location() {

        // какая-то требуемая проверка разрешений
        if (ActivityCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            return
        }

        // получаем локацию (lon, lat)
        fusedLocationClient.getCurrentLocation(PRIORITY_HIGH_ACCURACY, object : CancellationToken() {
            override fun onCanceledRequested(p0: OnTokenCanceledListener) = CancellationTokenSource().token
            override fun isCancellationRequested() = false
        })
            .addOnSuccessListener { location: Location? ->
                // если неудачно
                if (location == null) {
                    val msg = "Cannot get location."
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                }
                // если удачно
                else {
                    lat = location.latitude
                    lon = location.longitude
                }
            }
    }

    private fun Send() : Boolean {
        var success  = true
        val response : HttpResponse
        runBlocking() {
            val client = HttpClient(CIO) {
                install(ContentNegotiation) {
                    json(Json {
                        prettyPrint = true
                        isLenient = true
                    })
                }
                expectSuccess = false
            }
            try {
                response = client.post(url) {
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                append("lngtd", lon.toString())
                                append("lttd", lat.toString())
                                append("photo", image, Headers.build {
                                    append(HttpHeaders.ContentType, "image/jpg")
                                    append(HttpHeaders.ContentDisposition, "filename=\"img.jpg\"")
                                })
                            })
                    )
                }
                coords = response.body()
                if (coords.x == (-1).toFloat()) {
                    success = false
                    val msg = "Couldn't find a sun"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_LONG).show()
                    Log.d(TAG, msg)
                    return@runBlocking
                }

            } catch (exc: Exception) {
                success = false
                val msg = "No answer"
                Toast.makeText(baseContext, msg, Toast.LENGTH_LONG).show()
                Log.d(TAG, msg)
            }
        }

        return success
    }

    private fun calculation() {
        val a_hor: Float = calculate_a_hor() //good
        val a_ver: Float = calculate_a_ver() // good
        v_Img_Sun = coords.v_Img_RUB(a_hor, a_ver) // good
        v_Eph_Sun = coords.v_Eph_ENU() // good

        var v_ph_Sun = q_rot.rotate(v_Img_Sun)

        val norm_v_ph_Sun = v_ph_Sun.norm()
        val norm_v_Eph_Sun = v_Eph_Sun.norm()

        val scalProd = v_ph_Sun.scalarProd(v_Eph_Sun) // good
        val vectProd = v_ph_Sun.vectorProd(v_Eph_Sun) // good


        val cosAngle = scalProd / (norm_v_ph_Sun * norm_v_Eph_Sun)
        val sinAngle = vectProd.norm() / (norm_v_ph_Sun * norm_v_Eph_Sun)

        val cosHalfAngle = cos(acos(cosAngle) / 2)
        val sinHalfAngle = sin(asin(sinAngle) / 2) // good

        q_diff = Quaternion(cosHalfAngle.toDouble(), vectProd * sinHalfAngle)
        q_diff *= (1 / q_diff.abs())


        v_ph_Sun = q_diff.rotate(v_ph_Sun)
        /*val msg = "Calculated"
        Toast.makeText(baseContext, msg, Toast.LENGTH_LONG).show()
        Log.d(TAG, msg)*/
        //var a = q_rot.rotate(v_Eph_Sun)
        //val msg = a.x.toString() + ' ' + a.y.toString() + ' ' + a.z.toString() + ' ' +
        //        v_Img_Sun.x.toString() + ' ' + v_Img_Sun.y.toString() + ' ' + v_Img_Sun.z.toString()
        val msg = v_Eph_Sun.x.toString() + ' ' + v_Eph_Sun.y.toString() + ' ' + v_Eph_Sun.z.toString() + ' ' +
                v_ph_Sun.x.toString() + ' ' + v_ph_Sun.y.toString() + ' ' + v_ph_Sun.z.toString()
        //val msg = q_diff.x.toString() + ' ' + q_diff.y.toString() + ' ' + q_diff.z.toString() + ' ' +
        //               q_diff.w.toString() + q_diff.abs().toString()
        Toast.makeText(baseContext, msg, Toast.LENGTH_LONG).show()
        Log.d(TAG, msg)
        viewBinding.textView2.text = msg

        calc = true
    }

    private fun takePhoto() {
        imageCapture ?: return

        // get picture
        get_Image()

        // тут надо запомнить кватернион

        // получаем локацию (lon, lat)
        get_Location()

        // если передача данных успешна
         if (Send()) {
             calculation()
         }

    }


    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder().build()

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))


    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXApp"
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACTIVITY_RECOGNITION,
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }

    private val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions())
        { permissions ->
            // Handle Permission granted/rejected
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && it.value == false)
                    permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(baseContext,
                    "Permission request denied",
                    Toast.LENGTH_SHORT).show()
            } else {
                startCamera()
            }
        }

    private fun calculate_a_hor(): Float {
        return coords.a_hor(sa.horizonalAngle, width)
    }

    private fun calculate_a_ver(): Float {
        return coords.a_ver(sa.verticalAngle, height)
    }
    private fun DtR(x: Float): Float {
        return x * 180 / PI.toFloat()
    }

    lateinit var mGravity: FloatArray
    lateinit var mGeomagnetic: FloatArray
    lateinit var mRotationMatrix: FloatArray

    override fun onSensorChanged(event: SensorEvent) {

        try {
            if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {


                /*val q = FloatArray(4)
                getQuaternionFromVector(q, event.values)*/

                //q_rot = Quaternion(event.values[3].toDouble(), event.values[1].toDouble(),
                //    event.values[0].toDouble(), -event.values[2].toDouble()) // NED

                //q_rot = Quaternion(event.values[3].toDouble(), -event.values[2].toDouble(),
                //        event.values[0].toDouble(), -event.values[1].toDouble()) // FRD

                q_rot = Quaternion(event.values[3].toDouble(), event.values[0].toDouble(),
                        event.values[1].toDouble(), event.values[2].toDouble()) // ENU

                /*var r = Vector(0.0F, 0.0F, -1.0F)
                val b = q_rot.rotate(r)
                var x = b.x
                var y = b.y
                var z = b.z

                viewBinding.textView2.text = String.format("%.3f,    %.3f,    %.3f", x, y, z)*/

                /*val a = event.values
                var x = q_rot.x
                var y = q_rot.y
                var z = q_rot.z
                var w = q_rot.w
                viewBinding.textView2.text = String.format("%.3f,    %.3f,    %.3f,    %.3f", x, y, z, w)*/
                /*if (calc) {
                    v_frontNED = q_diff.rotate(q_rot.rotate(v_frontFRD))
                    var x = v_frontNED.x
                    var y = v_frontNED.y
                    var z = v_frontNED.z
                    viewBinding.textView2.text = String.format("%.3f,    %.3f,    %.3f", x, y, z)
                }
                else {
                    v_frontNED = q_rot.rotate(v_frontFRD)
                    var x = v_frontNED.x
                    var y = v_frontNED.y
                    var z = v_frontNED.z
                    var w = q_rot.w
                    var a = q_rot.x
                    var b = q_rot.y
                    var c = q_rot.z
                    viewBinding.textView2.text = String.format("%.3f,    %.3f,    %.3f,    %.3f,    %.3f,    %.3f,    %.3f",
                        x, y, z, w, a,b,c)
                }*/
            }
            /*if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) mGravity = event.values
            if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) mGeomagnetic = event.values
            if (mGravity != null && mGeomagnetic != null) {
                val R = FloatArray(9)
                val I = FloatArray(9)
                val success = getRotationMatrix(R, I, mGravity, mGeomagnetic)
                if (success) {
                    val orientation = FloatArray(3)
                    getOrientation(R, orientation)
                    val q = FloatArray(4)
                    getQuaternionFromVector(q, orientation)
                    q_rot = Quaternion(q[0].toDouble(), q[2].toDouble(), q[1].toDouble(), -q[3].toDouble()) // NED
                    if (calc) {
                        v_frontNED = q_diff.rotate(q_rot.rotate(v_frontFRD))
                        var x = v_frontNED.x
                        var y = v_frontNED.y
                        var z = v_frontNED.z
                        viewBinding.textView2.text = String.format("%.3f,    %.3f,    %.3f", x, y, z)
                    }
                    else {
                        v_frontNED = q_rot.rotate(v_frontFRD)
                        var x = v_frontNED.x
                        var y = v_frontNED.y
                        var z = v_frontNED.z
                        var a = q_rot.x
                        var b = q_rot.y
                        var c = q_rot.z
                        viewBinding.textView2.text = String.format("%.3f,    %.3f,    %.3f,    %.3f,    %.3f,    %.3f",
                            x, y, z,a,b,c)
                    }

                }
            }*/
        } catch (_:Exception) { }

    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { }

}

