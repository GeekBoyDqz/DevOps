#!groovy
@Library('jenkinslib') _     

pipeline{
    agent {
        label 'build'
    }

    environment {
        VERSION_CODE = "${VERSION_CODE}"
        APP_VERSION = "${APP_VERSION}"
        //APK包文件名称
        appName = "${appName}"
    }
    
    stages{
        stage("GetCode"){
            steps {
                script {
                    checkout([$class: 'GitSCM', branches: [[name: '${branchName}']], extensions: [], userRemoteConfigs: [[credentialsId: 'gitlab-admin-user', url: '${srcUrl}']]])
                }
            }
        }

        stage("正式包构建") {
            steps{
                script{
                    sh '''
                        gradle assemble -PVERSION_CODE=${VERSION_CODE} -PAPP_VERSION=${APP_VERSION} -PDEBUGGABLE=${DEBUGGABLE}
                    '''                   
                }
            }
        }

        stage('APK加固') {
            steps {
                script {         
                    withCredentials([usernamePassword(credentialsId: '360jiagu-admin-token', passwordVariable: 'passwd', usernameVariable: 'user'), usernamePassword(credentialsId: '360jiagu-keystore-token', passwordVariable: 'aliasPasswd', usernameVariable: 'keyPasswd')]) 
                        sh """
                            java -jar ${jiaguPath} -login ${user} ${passwd}
                            java -jar ${jiaguPath} -importsign ./app/app.jks ${keyPasswd} [这是别名] ${aliasPasswd}
                            ## 可以通过下面的配置单独选择需要加固增强服务配置
                            java -jar ${jiaguPath} -config -update -crashlog -x86
                            ## 生成加固包
                            java -jar ${jiaguPath} -jiagu ${jiaguApkPath} ${outjiaguApk} -autosign -automulpkg
                        """
                    }
                }

            }
        }
        stage('发布普通制品') {
            steps {
                script {
                    sh """
                        cp app/build/outputs/apk/debug/内测版_v*_debug.apk ./${appName}_内测版_v${APP_VERSION}.apk
                        ##上传蒲公英
                        appPath = "./${appName}_内测版_v${APP_VERSION}.apk"
                        reqUrl = "https://upload.pgyer.com/apiv1/app/upload"
                        curl -F "file=@${appPath}" -F "uKey=${ukey}" -F "_api_key=${apikey}" ${reqUrl}
                    """
                }
            }
        }
    }
   
    post {
        success {
            script {
                echo "success"
            }
        }
        failure {
            script {
                echo "failure"
            }
        }
        aborted {           
            script {
                echo "aborted"
            }
        }
    }
}
