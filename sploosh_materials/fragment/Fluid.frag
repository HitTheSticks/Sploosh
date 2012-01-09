//Fluid.frag
#ifdef USE_TEXTURE
uniform sampler2D m_Texture;
#endif
 
uniform vec4 m_StartColor;
uniform vec4 m_EndColor;
 
in vec3 vars;
 
#ifdef USE_TEXTURE
 
void main(){
  float ageFract = vars.x / vars.y;
  vec4 ageColor = mix(m_StartColor, m_EndColor, ageFract);
 
  vec2 uv = gl_PointCoord.xy;
  vec4 texColor = texture2D(m_Texture, uv);
 
#ifdef PURE_ALPHA_MAP
  ageColor.a *= texColor.r;
  gl_FragColor = ageColor;
#else
  gl_FragColor = texColor * ageColor;
#endif
}
 
#else //just color
void main(){
  float ageFract = vars.x / vars.y;
  gl_FragColor = mix(m_StartColor, m_EndColor, ageFract);
}
 
#endif //USE_TEXTURE