pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // 阿里云jcenter镜像 需要加快依然在jcenter第三方lib的升级速度
        maven(url = "https://maven.aliyun.com/repository/jcenter")
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
include(":mzbanner")
include(":sticky-headers-recyclerview")
include(":blurengine")
