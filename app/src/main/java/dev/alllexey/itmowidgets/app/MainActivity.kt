package dev.alllexey.itmowidgets.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import dev.alllexey.itmowidgets.app.shell.ShellHost
import dev.alllexey.itmowidgets.feature.sport.navigation.SportRoutes
import dev.alllexey.itmowidgets.feature.update.navigation.UpdateRoutes

/** The app's one activity: it hosts the Navigation 3 shell ([ShellHost]), which does the rest. */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ShellHost(this, updateRoute = UpdateRoutes::of, featureRoutes = FEATURE_ROUTES)
    }

    private companion object {
        /** The feature modules' keys the shell saves with its back stack and pending route. */
        val FEATURE_ROUTES = listOf(UpdateRoutes.registration, SportRoutes.registration)
    }
}
