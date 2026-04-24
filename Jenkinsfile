pipeline {
    agent any

    parameters {
        choice(
            name: 'SPRING_PROFILE',
            choices: ['dev', 'prod'],
            description: 'Select Spring profile for deployment'
        )
        string(
            name: 'BUILD_VERSION',
            defaultValue: '1.0-SNAPSHOT',
            description: 'Build version'
        )
    }

    environment {
        // Application settings
        APP_NAME = 'dbna-outbound'
        GRADLE_CACHE = '.gradle'
        DOCKER_REGISTRY = credentials('docker-registry-url')
        DOCKER_CREDENTIALS = credentials('docker-credentials-id')

        // Profile-specific settings
        SPRING_PROFILES_ACTIVE = "${params.SPRING_PROFILE}"

        // For prod profile - set environment variables (adjust as needed)
        DBNA_FROM_PARTY_ID = credentials("dbna-from-party-id-${params.SPRING_PROFILE}")
    }

    stages {
        stage('Initialize') {
            steps {
                script {
                    echo "=========================================="
                    echo "Building with Spring Profile: ${params.SPRING_PROFILE}"
                    echo "Build Version: ${params.BUILD_VERSION}"
                    echo "=========================================="

                    // Print environment info
                    sh '''
                        echo "Java version:"
                        java -version
                        echo ""
                        echo "Gradle version:"
                        ./gradlew --version
                    '''
                }
            }
        }

        stage('Clean') {
            steps {
                script {
                    echo "Cleaning previous build artifacts..."
                    sh './gradlew clean'
                }
            }
        }

        stage('Build') {
            steps {
                script {
                    echo "Building application with ${params.SPRING_PROFILE} profile..."
                    sh '''
                        ./gradlew build \
                            -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE} \
                            -x test \
                            --info
                    '''
                }
            }
        }

        stage('Unit Tests') {
            steps {
                script {
                    echo "Running unit tests with ${params.SPRING_PROFILE} profile..."
                    sh '''
                        ./gradlew test \
                            -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE} \
                            --info
                    '''
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                script {
                    echo "Building Docker image for ${params.SPRING_PROFILE} profile..."
                    sh '''
                        docker build \
                            --no-cache \
                            --build-arg SPRING_PROFILE=${SPRING_PROFILES_ACTIVE} \
                            --build-arg BUILD_VERSION=${BUILD_VERSION} \
                            -t ${APP_NAME}:${BUILD_VERSION}-${SPRING_PROFILES_ACTIVE} \
                            -f Dockerfile \
                            .
                    '''
                }
            }
        }

        stage('Push Docker Image') {
            when {
                expression {
                    return env.BRANCH_NAME == 'main' || env.BRANCH_NAME == 'master'
                }
            }
            steps {
                script {
                    echo "Pushing Docker image to registry..."
                    sh '''
                        echo "${DOCKER_CREDENTIALS_PSW}" | docker login -u "${DOCKER_CREDENTIALS_USR}" --password-stdin "${DOCKER_REGISTRY}"
                        docker tag ${APP_NAME}:${BUILD_VERSION}-${SPRING_PROFILES_ACTIVE} ${DOCKER_REGISTRY}/${APP_NAME}:${BUILD_VERSION}-${SPRING_PROFILES_ACTIVE}
                        docker push ${DOCKER_REGISTRY}/${APP_NAME}:${BUILD_VERSION}-${SPRING_PROFILES_ACTIVE}
                        docker logout "${DOCKER_REGISTRY}"
                    '''
                }
            }
        }

        stage('Deploy') {
            when {
                expression {
                    return env.BRANCH_NAME == 'main' || env.BRANCH_NAME == 'master'
                }
            }
            steps {
                script {
                    echo "Deploying to ${params.SPRING_PROFILE} environment..."

                    if (params.SPRING_PROFILE == 'dev') {
                        sh '''
                            ./deploy-local.sh
                        '''
                    } else if (params.SPRING_PROFILE == 'prod') {
                        sh '''
                            ./deploy.sh
                        '''
                    }
                }
            }
        }

        stage('Validate Deployment') {
            when {
                expression {
                    return env.BRANCH_NAME == 'main' || env.BRANCH_NAME == 'master'
                }
            }
            steps {
                script {
                    echo "Validating deployment for ${params.SPRING_PROFILE} profile..."
                    sh '''
                        # Check if application is running
                        sleep 10
                        curl -f http://localhost:8080/actuator/health || exit 1
                        echo "Application health check passed!"
                    '''
                }
            }
        }
    }

    post {
        always {
            script {
                echo "=========================================="
                echo "Build completed for ${params.SPRING_PROFILE} profile"
                echo "=========================================="
            }

            // Archive test results
            junit testResults: '**/build/test-results/test/*.xml', allowEmptyResults: true

            // Clean workspace
            cleanWs()
        }

        success {
            script {
                echo "✓ Pipeline succeeded for ${params.SPRING_PROFILE} profile"
                // Add success notifications here (email, Slack, etc.)
            }
        }

        failure {
            script {
                echo "✗ Pipeline failed for ${params.SPRING_PROFILE} profile"
                // Add failure notifications here (email, Slack, etc.)
            }
        }
    }
}

