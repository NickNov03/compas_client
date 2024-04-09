package com.android.example.cameraxapp

import kotlinx.serialization.Serializable
import kotlin.math.cos
import kotlin.math.sin

@Serializable
data class Coords(var Az : Float, var El : Float, var x: Float, var y: Float) {

    // угол между центром камеры и Солнцем (если Солнце правее центра, то угол>0)
    // в горизонтальной ПРОЕКЦИИ
    public fun a_hor(a_hor_cam: Float, width_cam : Int) : Float {
        // a_hor_cam - горизонтальный угол обзора камеры
        // width_cam - ширина изображения в пикселях
        val k :Float = a_hor_cam / width_cam
        val x_center : Int = (width_cam / 2).toInt()
        return (x - x_center) * k
    }

    // угол между центром камеры и Солнцем (если Солнце выше центра, то угол>0)
    // в вертикальной ПРОЕКЦИИ
    public fun a_ver(a_ver_cam: Float, height_cam : Int) : Float {
        // a_ver_cam - вертикальный угол обзора камеры
        // height_cam - высота изображения в пикселях
        val k : Float = a_ver_cam / height_cam
        val y_center : Int = (height_cam / 2).toInt()
        return (y - y_center) * k
    }

    // вектор на Солнце в FRD
    public fun v_Img(a_hor:Float, a_ver:Float) : Vector {
        return Vector((cos(a_ver) * cos(a_hor)).toDouble(), (cos(a_ver) * sin(a_hor)).toDouble(),
            (-sin(a_ver)).toDouble()
        )
    }
    // вектор на Солнце в NED
    public fun v_Eph() : Vector {
        return Vector((cos(El) * cos(Az)).toDouble(), (cos(El) * sin(Az)).toDouble(),
            (-sin(El)).toDouble()
        )
    }

    // вектор на Солнце в RUB
    public fun v_Img_RUB(a_hor:Float, a_ver:Float) : Vector {
        return Vector((cos(a_ver) * sin(a_hor)).toDouble(), sin(a_ver).toDouble(),
            (-cos(a_ver) * cos(a_hor)).toDouble()
        )
    }
    // вектор на Солнце в ENU
    public fun v_Eph_ENU() : Vector {
        return Vector((cos(El) * sin(Az)).toDouble(), (cos(El) * cos(Az)).toDouble(),
            sin(El).toDouble()
        )
    }
}