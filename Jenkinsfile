pipeline {
    agent any 
    stages {
        stage('build') {
            steps {
                sh 'echo "Run maven"'
                sh 'mvn --version'
                sh 'echo "maven test"'
                sh 'mvn test'
            }
        }
    }
}
