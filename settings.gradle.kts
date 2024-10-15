pluginManagement {
    repositories {
        google()
        mavenCentral()
        // Warning: this repository is going to shut down soon
        jcenter()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Warning: this repository is going to shut down soon
        jcenter()
        maven(url = "https://jitpack.io")
        maven {
            url = uri("http://nexus.igancao.com/repository/3rd_party/")
            isAllowInsecureProtocol = true
            credentials {
                username = "developer"
                password = "72fb25481e"
            }
        }
    }
}

rootProject.name = "gc_android_common"

include(":app")
include(":bga-base-adapter")
include(":wheelview")
include(":pickerview")
include(":swiper_recyclerview")
include(":fragmentation")
include(":labelview")
include(":guide")
include(":liveeventbus-x")
include(":bga-photo-picker")
include(":recyclerview_divider")
include(":circleprogressbar")
include(":wavesidebar")
