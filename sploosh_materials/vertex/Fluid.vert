//Fluid.vert
uniform mat4 g_WorldViewProjectionMatrix;
 
uniform float m_StartSize;
uniform float m_EndSize;
 
attribute vec3 inPosition;
attribute vec3 inBindPosePosition;
 
out vec3 vars;
 
void main(){
  vec4 pos = vec4(inPosition, 1.0);
  vars = inBindPosePosition;
 
  float ageFract = vars.x / vars.y;
  gl_PointSize = mix(m_StartSize, m_EndSize, ageFract);
 
  gl_Position = g_WorldViewProjectionMatrix * pos;
}
