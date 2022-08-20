//For Bluetooth
String Received[];
#define motor1A 10
#define motor1B 12
#define motor2A 11
#define motor2B 13
// For buzzer , Distance Sensor
#define trigger 3
#define echoIn 2
#define beeb 8
int timer;
int distance;
//For RGB , LDR
#define red 6
#define green 7
#define blue 5
int ldr;
#define LDRi A15

void setup() {
  // put your setup code here, to run once:
      Serial.begin(9600);
      Serial3.begin(38400);
      pinMode(motor1A, OUTPUT);
      pinMode(motor1B, OUTPUT);
      pinMode(motor2A, OUTPUT);
      pinMode(motor2B, OUTPUT);

    pinMode(trigger,OUTPUT);
    pinMode(echoIn,INPUT);
    pinMode(beeb,OUTPUT);
    digitalWrite(trigger,0);
}

void loop() {
  // put your main code here, to run repeatedly:
   checkSurrounding();
   checkLighting();
   if(Serial3.available()){
     Received = Serial3.read();
     analogWrite(red,0);
     analogWrite(green,0);
     analogWrite(blue,250);
     if(Received == 'F'){//in case the object is in front of us
               if(checkArrived()){
               Serial3.write('S');
               return;
              }
             digitalWrite(motor1A,HIGH);
             digitalWrite(motor2A,LOW);
             digitalWrite(motor1B,HIGH);
             digitalWrite(motor2B,LOW);
      }
     else if(Received == 'L'){
             digitalWrite(motor1A,HIGH);
             digitalWrite(motor2A,LOW);
             digitalWrite(motor1B,LOW);
             digitalWrite(motor2B,LOW);
      }
     else if(Received == 'R'){
             digitalWrite(motor1B,HIGH);
             digitalWrite(motor2B,LOW);
             digitalWrite(motor1A,LOW);
             digitalWrite(motor2A,LOW);
      }
     else if(Received == 'B'){
             digitalWrite(motor1A,LOW);
             digitalWrite(motor2A,HIGH);
             digitalWrite(motor1B,LOW);
             digitalWrite(motor2B,HIGH);
      }
      else if(Received == 'S'){
             digitalWrite(motor1A,LOW);
             digitalWrite(motor2A,LOW);
             digitalWrite(motor1B,LOW);
             digitalWrite(motor2B,LOW);
        }
   }else{
             analogWrite(red,250);
             analogWrite(green,0);
             analogWrite(blue,0);
    }
}

void checkSurrounding(){
    digitalWrite(trigger,HIGH );
    delayMicroseconds(10);
    digitalWrite(trigger,LOW);
    timer = pulseIn(echoIn,HIGH);
    distance = timer / 58;
    Serial.println(distance);
    if(distance <= 20 && distance > 0)digitalWrite(beeb,HIGH);
    else digitalWrite(beeb,LOW);
  }

void checkLighting(){
  ldr = analogRead(LDRi);
  if(ldr < 300){
     analogWrite(red,250);
     analogWrite(green,250);
     analogWrite(blue,250);
    }
    else{
     analogWrite(red,0);
     analogWrite(green,0);
     analogWrite(blue,0);
      }
  }

bool checkArrived(){
     timer = pulseIn(echoIn,HIGH);
     distance = timer / 58;
     Serial.println(distance);
     if(distance <= 20 && distance > 0){//Arrived at object
        analogWrite(red,0);
        analogWrite(green,250);
        analogWrite(blue,0);
        return true;
       }
      else {return false;}
}
