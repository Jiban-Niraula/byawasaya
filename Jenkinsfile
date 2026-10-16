pipeline {
  agent any

  options {
    timestamps()
    disableConcurrentBuilds()
    buildDiscarder(logRotator(numToKeepStr: '20'))
  }

  stages {
    stage('Frontend') {
      steps {
        dir('frontend') {
          sh 'npm ci'
          sh 'npm run lint'
          sh 'npm run build'
        }
      }
    }

    stage('Backend') {
      steps {
        dir('backend') {
          sh 'chmod +x mvnw'
          sh './mvnw -B verify'
        }
      }
    }

    stage('Container build') {
      steps {
        sh 'docker build -t byawasaya-backend:ci ./backend'
        sh 'docker build -t byawasaya-frontend:ci ./frontend'
      }
    }
  }
}
