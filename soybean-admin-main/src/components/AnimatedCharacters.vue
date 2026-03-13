<template>
  <div class="relative scale-75 xl:scale-100 origin-center" style="width: 550px; height: 400px">
    
    <div 
      ref="purpleRef"
      class="absolute bottom-0 transition-all duration-700 ease-in-out"
      :style="{
        left: '70px', width: '180px',
        height: (isTyping || isHidingPassword) ? '440px' : '400px',
        backgroundColor: '#6C3FF5', borderRadius: '10px 10px 0 0', zIndex: 1,
        transform: (passwordLength > 0 && showPassword) ? 'skewX(0deg)' : 
                   (isTyping || isHidingPassword) ? `skewX(${purplePos.bodySkew - 12}deg) translateX(40px)` : 
                   `skewX(${purplePos.bodySkew}deg)`,
        transformOrigin: 'bottom center'
      }"
    >
      <div class="absolute flex gap-8 transition-all duration-700 ease-in-out"
           :style="{
             left: (passwordLength > 0 && showPassword) ? '20px' : isLookingAtEachOther ? '55px' : `${45 + purplePos.faceX}px`,
             top: (passwordLength > 0 && showPassword) ? '35px' : isLookingAtEachOther ? '65px' : `${40 + purplePos.faceY}px`
           }">
        <div ref="purpleEye1" class="rounded-full flex items-center justify-center transition-all duration-150 bg-white overflow-hidden" :style="{ width: '18px', height: isPurpleBlinking ? '2px' : '18px' }">
          <div v-if="!isPurpleBlinking" class="rounded-full bg-[#2D2D2D] transition-transform duration-100 ease-out" style="width: 7px; height: 7px;" :style="{ transform: getPupilTransform(purpleEye1, 5, (passwordLength > 0 && showPassword) ? (isPurplePeeking ? 4 : -4) : isLookingAtEachOther ? 3 : undefined, (passwordLength > 0 && showPassword) ? (isPurplePeeking ? 5 : -4) : isLookingAtEachOther ? 4 : undefined) }" />
        </div>
        <div ref="purpleEye2" class="rounded-full flex items-center justify-center transition-all duration-150 bg-white overflow-hidden" :style="{ width: '18px', height: isPurpleBlinking ? '2px' : '18px' }">
          <div v-if="!isPurpleBlinking" class="rounded-full bg-[#2D2D2D] transition-transform duration-100 ease-out" style="width: 7px; height: 7px;" :style="{ transform: getPupilTransform(purpleEye2, 5, (passwordLength > 0 && showPassword) ? (isPurplePeeking ? 4 : -4) : isLookingAtEachOther ? 3 : undefined, (passwordLength > 0 && showPassword) ? (isPurplePeeking ? 5 : -4) : isLookingAtEachOther ? 4 : undefined) }" />
        </div>
      </div>
    </div>

    <div 
      ref="blackRef"
      class="absolute bottom-0 transition-all duration-700 ease-in-out"
      :style="{
        left: '240px', width: '120px', height: '310px',
        backgroundColor: '#2D2D2D', borderRadius: '8px 8px 0 0', zIndex: 2,
        transform: (passwordLength > 0 && showPassword) ? 'skewX(0deg)' : 
                   isLookingAtEachOther ? `skewX(${blackPos.bodySkew * 1.5 + 10}deg) translateX(20px)` : 
                   (isTyping || isHidingPassword) ? `skewX(${blackPos.bodySkew * 1.5}deg)` : 
                   `skewX(${blackPos.bodySkew}deg)`,
        transformOrigin: 'bottom center'
      }"
    >
      <div class="absolute flex gap-6 transition-all duration-700 ease-in-out"
           :style="{
             left: (passwordLength > 0 && showPassword) ? '10px' : isLookingAtEachOther ? '32px' : `${26 + blackPos.faceX}px`,
             top: (passwordLength > 0 && showPassword) ? '28px' : isLookingAtEachOther ? '12px' : `${32 + blackPos.faceY}px`
           }">
        <div ref="blackEye1" class="rounded-full flex items-center justify-center transition-all duration-150 bg-white overflow-hidden" :style="{ width: '16px', height: isBlackBlinking ? '2px' : '16px' }">
          <div v-if="!isBlackBlinking" class="rounded-full bg-[#2D2D2D] transition-transform duration-100 ease-out" style="width: 6px; height: 6px;" :style="{ transform: getPupilTransform(blackEye1, 4, (passwordLength > 0 && showPassword) ? -4 : isLookingAtEachOther ? 0 : undefined, (passwordLength > 0 && showPassword) ? -4 : isLookingAtEachOther ? -4 : undefined) }" />
        </div>
        <div ref="blackEye2" class="rounded-full flex items-center justify-center transition-all duration-150 bg-white overflow-hidden" :style="{ width: '16px', height: isBlackBlinking ? '2px' : '16px' }">
          <div v-if="!isBlackBlinking" class="rounded-full bg-[#2D2D2D] transition-transform duration-100 ease-out" style="width: 6px; height: 6px;" :style="{ transform: getPupilTransform(blackEye2, 4, (passwordLength > 0 && showPassword) ? -4 : isLookingAtEachOther ? 0 : undefined, (passwordLength > 0 && showPassword) ? -4 : isLookingAtEachOther ? -4 : undefined) }" />
        </div>
      </div>
    </div>

    <div 
      ref="orangeRef"
      class="absolute bottom-0 transition-all duration-700 ease-in-out"
      :style="{
        left: '0px', width: '240px', height: '200px', zIndex: 3,
        backgroundColor: '#FF9B6B', borderRadius: '120px 120px 0 0',
        transform: (passwordLength > 0 && showPassword) ? 'skewX(0deg)' : `skewX(${orangePos.bodySkew}deg)`,
        transformOrigin: 'bottom center'
      }"
    >
      <div class="absolute flex gap-8 transition-all duration-200 ease-out"
           :style="{
             left: (passwordLength > 0 && showPassword) ? '50px' : `${82 + orangePos.faceX}px`,
             top: (passwordLength > 0 && showPassword) ? '85px' : `${90 + orangePos.faceY}px`
           }">
        <div ref="orangeEye1" class="rounded-full bg-[#2D2D2D] transition-transform duration-100 ease-out" style="width: 12px; height: 12px;" :style="{ transform: getPupilTransform(orangeEye1, 5, (passwordLength > 0 && showPassword) ? -5 : undefined, (passwordLength > 0 && showPassword) ? -4 : undefined) }" />
        <div ref="orangeEye2" class="rounded-full bg-[#2D2D2D] transition-transform duration-100 ease-out" style="width: 12px; height: 12px;" :style="{ transform: getPupilTransform(orangeEye2, 5, (passwordLength > 0 && showPassword) ? -5 : undefined, (passwordLength > 0 && showPassword) ? -4 : undefined) }" />
      </div>
    </div>

    <div 
      ref="yellowRef"
      class="absolute bottom-0 transition-all duration-700 ease-in-out"
      :style="{
        left: '310px', width: '140px', height: '230px', zIndex: 4,
        backgroundColor: '#E8D754', borderRadius: '70px 70px 0 0',
        transform: (passwordLength > 0 && showPassword) ? 'skewX(0deg)' : `skewX(${yellowPos.bodySkew}deg)`,
        transformOrigin: 'bottom center'
      }"
    >
      <div class="absolute flex gap-6 transition-all duration-200 ease-out"
           :style="{
             left: (passwordLength > 0 && showPassword) ? '20px' : `${52 + yellowPos.faceX}px`,
             top: (passwordLength > 0 && showPassword) ? '35px' : `${40 + yellowPos.faceY}px`
           }">
        <div ref="yellowEye1" class="rounded-full bg-[#2D2D2D] transition-transform duration-100 ease-out" style="width: 12px; height: 12px;" :style="{ transform: getPupilTransform(yellowEye1, 5, (passwordLength > 0 && showPassword) ? -5 : undefined, (passwordLength > 0 && showPassword) ? -4 : undefined) }" />
        <div ref="yellowEye2" class="rounded-full bg-[#2D2D2D] transition-transform duration-100 ease-out" style="width: 12px; height: 12px;" :style="{ transform: getPupilTransform(yellowEye2, 5, (passwordLength > 0 && showPassword) ? -5 : undefined, (passwordLength > 0 && showPassword) ? -4 : undefined) }" />
      </div>
      <div class="absolute w-20 h-[4px] bg-[#2D2D2D] rounded-full transition-all duration-200 ease-out"
           :style="{
             left: (passwordLength > 0 && showPassword) ? '10px' : `${40 + yellowPos.faceX}px`,
             top: (passwordLength > 0 && showPassword) ? '88px' : `${88 + yellowPos.faceY}px`
           }" />
    </div>

  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, watch } from 'vue';

const props = defineProps({
  isTyping: { type: Boolean, default: false },
  showPassword: { type: Boolean, default: false },
  passwordLength: { type: Number, default: 0 }
});

const mouseX = ref(0);
const mouseY = ref(0);

// DOM Refs for character bodies
const purpleRef = ref<HTMLElement | null>(null);
const blackRef = ref<HTMLElement | null>(null);
const orangeRef = ref<HTMLElement | null>(null);
const yellowRef = ref<HTMLElement | null>(null);

// DOM Refs for eyes
const purpleEye1 = ref<HTMLElement | null>(null);
const purpleEye2 = ref<HTMLElement | null>(null);
const blackEye1 = ref<HTMLElement | null>(null);
const blackEye2 = ref<HTMLElement | null>(null);
const orangeEye1 = ref<HTMLElement | null>(null);
const orangeEye2 = ref<HTMLElement | null>(null);
const yellowEye1 = ref<HTMLElement | null>(null);
const yellowEye2 = ref<HTMLElement | null>(null);

// States
const isPurpleBlinking = ref(false);
const isBlackBlinking = ref(false);
const isLookingAtEachOther = ref(false);
const isPurplePeeking = ref(false);

const isHidingPassword = computed(() => props.passwordLength > 0 && !props.showPassword);

const handleMouseMove = (e: MouseEvent) => {
  mouseX.value = e.clientX;
  mouseY.value = e.clientY;
};

// Blinking Logic
const scheduleBlink = (stateRef: any) => {
  const interval = Math.random() * 4000 + 3000;
  const timeoutId = setTimeout(() => {
    stateRef.value = true;
    setTimeout(() => {
      stateRef.value = false;
      scheduleBlink(stateRef);
    }, 150);
  }, interval);
  return timeoutId;
};

// Observers and Timers
let purpleBlinkTimer: any;
let blackBlinkTimer: any;
let lookTimer: any;
let peekTimer: any;

onMounted(() => {
  window.addEventListener('mousemove', handleMouseMove);
  purpleBlinkTimer = scheduleBlink(isPurpleBlinking);
  blackBlinkTimer = scheduleBlink(isBlackBlinking);
});

onUnmounted(() => {
  window.removeEventListener('mousemove', handleMouseMove);
  clearTimeout(purpleBlinkTimer);
  clearTimeout(blackBlinkTimer);
  clearTimeout(lookTimer);
  clearTimeout(peekTimer);
});

watch(() => props.isTyping, (val) => {
  if (val) {
    isLookingAtEachOther.value = true;
    clearTimeout(lookTimer);
    lookTimer = setTimeout(() => { isLookingAtEachOther.value = false; }, 800);
  } else {
    isLookingAtEachOther.value = false;
  }
});

watch(() => [props.passwordLength, props.showPassword], ([len, show]) => {
  if (typeof len === 'number' && len > 0 && show) {
    const schedulePeek = () => {
      peekTimer = setTimeout(() => {
        isPurplePeeking.value = true;
        setTimeout(() => { isPurplePeeking.value = false; }, 800);
      }, Math.random() * 3000 + 2000);
    };
    schedulePeek();
  } else {
    isPurplePeeking.value = false;
    clearTimeout(peekTimer);
  }
});

// Calculate Face/Body Skew based on mouse position
const calculatePosition = (refEl: HTMLElement | null) => {
  if (!refEl) return { faceX: 0, faceY: 0, bodySkew: 0 };
  const rect = refEl.getBoundingClientRect();
  const centerX = rect.left + rect.width / 2;
  const centerY = rect.top + rect.height / 3;
  
  const deltaX = mouseX.value - centerX;
  const deltaY = mouseY.value - centerY;

  return {
    faceX: Math.max(-15, Math.min(15, deltaX / 20)),
    faceY: Math.max(-10, Math.min(10, deltaY / 30)),
    bodySkew: Math.max(-6, Math.min(6, -deltaX / 120))
  };
};

const purplePos = computed(() => calculatePosition(purpleRef.value));
const blackPos = computed(() => calculatePosition(blackRef.value));
const orangePos = computed(() => calculatePosition(orangeRef.value));
const yellowPos = computed(() => calculatePosition(yellowRef.value));

// Calculate exact pupil transform
const getPupilTransform = (eyeEl: HTMLElement | null, maxDistance: number, forceX?: number, forceY?: number) => {
  if (forceX !== undefined && forceY !== undefined) {
    return `translate(${forceX}px, ${forceY}px)`;
  }
  if (!eyeEl) return `translate(0px, 0px)`;
  
  const rect = eyeEl.getBoundingClientRect();
  const eyeCenterX = rect.left + rect.width / 2;
  const eyeCenterY = rect.top + rect.height / 2;
  
  const deltaX = mouseX.value - eyeCenterX;
  const deltaY = mouseY.value - eyeCenterY;
  const distance = Math.min(Math.sqrt(deltaX ** 2 + deltaY ** 2), maxDistance);
  const angle = Math.atan2(deltaY, deltaX);
  
  return `translate(${Math.cos(angle) * distance}px, ${Math.sin(angle) * distance}px)`;
};
</script>