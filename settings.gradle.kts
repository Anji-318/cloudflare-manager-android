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
        // 自动识别环境：CI/CD 环境（GitHub Actions、GitLab CI、Jenkins、Travis 等）
        // 这些服务器通常在国外，直接使用官方仓库速度更快
        val isCiEnv = System.getenv("CI") != null
                || System.getenv("GITHUB_ACTIONS") != null
                || System.getenv("GITLAB_CI") != null
                || System.getenv("JENKINS_HOME") != null
                || System.getenv("TRAVIS") != null
                || System.getenv("CIRCLECI") != null

        if (isCiEnv) {
            // CI 环境：使用官方仓库（国外服务器访问国内镜像反而慢）
            google()
            mavenCentral()
        } else {
            // 本地开发环境：使用国内镜像加速
            maven { url = uri("https://maven.aliyun.com/repository/public") }
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
            // 备用官方仓库
            google()
            mavenCentral()
        }
    }
}

rootProject.name = "CloudflareManager"
include(":app")
