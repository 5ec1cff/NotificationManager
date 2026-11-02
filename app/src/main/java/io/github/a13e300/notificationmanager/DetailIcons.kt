package io.github.a13e300.notificationmanager

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

internal object DetailIcons {
    val Back = ImageVector.Builder("Back", 24.dp, 24.dp, 24f, 24f, autoMirror = true)
        .addPath(PathParser().parsePathString("M20,11H7.8l5.6,-5.6L12,4l-8,8l8,8l1.4,-1.4L7.8,13H20z").toNodes(), fill = SolidColor(Color.Black))
        .build()
    val Settings = icon("Settings", "M3,5h10V3h2v2h6v2h-6v2h-2V7H3z M3,11h4V9h2v2h12v2H9v2H7v-2H3z M3,17h12v-2h2v2h4v2h-4v2h-2v-2H3z")
    val Delete = icon("Delete", "M8,3h8v2h5v2H3V5h5z M5,9h14v12H5z M8,11v8h2v-8z M14,11v8h2v-8z")
    val DeleteAll = icon("DeleteAll", "M2,6h8v2H2z M2,11h6v2H2z M2,16h6v2H2z M13,3h5v2h4v2H9V5h4z M10,9h11v12H10z M12,11v8h2v-8z M17,11v8h2v-8z")
    val Forward = ImageVector.Builder("Forward", 24.dp, 24.dp, 24f, 24f, autoMirror = true)
        .addPath(PathParser().parsePathString("M9,6l6,6l-6,6l-1.4,-1.4l4.6,-4.6l-4.6,-4.6z").toNodes(), fill = SolidColor(Color.Black))
        .build()
    val Refresh = icon("Refresh", "M19,4v4a8,8 0,1 0,1,9l-1.7,-1a6,6 0,1 1,-0.8,-6H14v2h7V4z")
    val Search = icon("Search", "M10,2a8,8 0,1 0,5,14.2l5.8,5.8l1.4,-1.4l-5.8,-5.8A8,8 0,0 0,10,2z M10,4a6,6 0,1 1,0,12a6,6 0,0 1,0,-12z")
    val App = icon("App", "M3,3h8v8H3z M13,3h8v8h-8z M3,13h8v8H3z M13,13h8v8h-8z")
    val Channel = icon("Channel", "M12,2a6,6 0,0 0,-6,6v5l-2,3v2h16v-2l-2,-3V8a6,6 0,0 0,-6,-6z M9,20h6a3,3 0,0 1,-6,0z")
    val Text = icon("Text", "M4,4h16v2H4z M4,9h16v2H4z M4,14h16v2H4z M4,19h10v2H4z")
    val Title = icon("Title", "M4,4h16v3h-6v14h-4V7H4z")
    val Identity = icon("Identity", "M8,3h2l-1,5h6l1,-5h2l-1,5h4v2h-4l-1,5h4v2h-5l-1,4h-2l1,-4H7l-1,4H4l1,-4H2v-2h4l1,-5H3V8h4z M9,10l-1,5h6l1,-5z")
    val Person = icon("Person", "M12,3a4,4 0,1 1,0,8a4,4 0,0 1,0,-8z M4,21v-3a8,5 0,0 1,16,0v3z")
    val Time = icon("Time", "M12,2a10,10 0,1 0,0,20a10,10 0,0 0,0,-20z M12,4a8,8 0,1 1,0,16a8,8 0,0 1,0,-16z M11,6h2v6l4,3l-1,1.6l-5,-3.6z")
    val Info = icon("Info", "M12,2a10,10 0,1 0,0,20a10,10 0,0 0,0,-20z M12,4a8,8 0,1 1,0,16a8,8 0,0 1,0,-16z M11,10h2v7h-2z M11,6h2v2h-2z")
    val Source = icon("Source", "M3,5h11v2H5v12h14v-9h2v11H3z M14,3h7v7h-2V6.4l-8,8l-1.4,-1.4l8,-8H14z")
    val Image = icon("Image", "M3,3h18v18H3z M5,5v14h14V5z M6,17l4,-6l3,4l2,-3l3,5z M15,6a2,2 0,1 1,0,4a2,2 0,0 1,0,-4z")
    val Code = icon("Code", "M8,5l1.4,1.4L3.8,12l5.6,5.6L8,19l-7,-7z M16,5l7,7l-7,7l-1.4,-1.4l5.6,-5.6l-5.6,-5.6z M13,3h2l-4,18H9z")
    val Action = icon("Action", "M4,3l16,9l-16,9v-7l11,-2l-11,-2z")
    val Copy = icon("Copy", "M7,2h14v16h-3V5H7z M3,6h13v16H3z M5,8v12h9V8z")

    private fun icon(name: String, path: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black))
        .build()
}
