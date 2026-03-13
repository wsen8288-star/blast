import type { GlobalThemeOverrides } from 'naive-ui';

// app theme preset color
export const appThemeList: string[] = [
  '#3b82f6', // Soybean 默认科技蓝 (替换了原来的 #2d8cf0)
  '#0960bd',
  '#0084f4',
  '#009688',
  '#536dfe',
  '#ff5c93',
  '#ee4f12',
  '#0096c7',
  '#9c27b0',
  '#ff9800',
  '#FF3D68',
  '#00C1D4',
  '#71EFA3',
  '#171010',
  '#78DEC7',
  '#1768AC',
  '#FB9300',
  '#FC5404',
];

export const darkIndustrialThemeOverrides: GlobalThemeOverrides = {
  common: {
    // === 基础色彩基调 (对接 Soybean 变量) ===
    bodyColor: '#101014',            // L0 最外层背景
    cardColor: '#18181c',            // L1 卡片/面板背景
    modalColor: '#26262a',           // 弹窗背景
    popoverColor: '#26262a',         // 悬浮面板背景 (下拉框、日期面板)
    
    // === 文字颜色体系 ===
    textColorBase: 'rgba(255, 255, 255, 0.82)',    // 常规正文
    textColor1: 'rgba(255, 255, 255, 0.9)',        // 标题/强调 (原 T1)
    textColor2: 'rgba(255, 255, 255, 0.82)',       // 常用正文 (原 T2)
    textColor3: 'rgba(255, 255, 255, 0.52)',       // 次要信息/占位符 (原 T3)
    
    // === 边框与分割线 ===
    borderColor: 'rgba(255, 255, 255, 0.09)',          
    dividerColor: 'rgba(255, 255, 255, 0.09)', 
    
    // === 悬浮与激活反馈 ===
    hoverColor: 'rgba(255, 255, 255, 0.09)',           // 悬浮底色
    pressedColor: 'rgba(255, 255, 255, 0.13)', 
    
    // === 品牌主色 (科技蓝) ===
    primaryColor: '#3B82F6', 
    primaryColorHover: '#60A5FA', 
    primaryColorPressed: '#2563EB', 
    primaryColorSuppl: '#3B82F6', 

    borderRadius: '10px',
  }, 
  Card: {
    borderColor: 'rgba(255, 255, 255, 0.09)',
    colorTarget: 'bg',
  },
  Menu: {
    itemColorActive: 'rgba(59, 130, 246, 0.15)',
    itemColorActiveHover: 'rgba(59, 130, 246, 0.2)',
    itemTextColorActive: '#3B82F6',
    itemIconColorActive: '#3B82F6',
  },
  Select: { 
    peers: { 
      InternalSelection: { 
        colorActive: '#18181c', // 激活时保持与卡片背景一致 
        boxShadowFocus: '0 0 0 2px rgba(59, 130, 246, 0.3)', 
      } 
    } 
  }, 
  InternalSelectMenu: { 
    color: '#26262a', 
    optionColorPending: 'rgba(255, 255, 255, 0.09)', 
    optionTextColorActive: '#3B82F6', 
    optionTextColorPressed: '#3B82F6', 
  }, 
  DataTable: { 
    thColor: '#18181c', 
    thTextColor: 'rgba(255, 255, 255, 0.52)', 
    tdColor: '#18181c', 
    tdColorHover: 'rgba(255, 255, 255, 0.06)', 
    borderColor: 'rgba(255, 255, 255, 0.09)',
  }, 
  Button: { 
    textColor: 'rgba(255, 255, 255, 0.82)', 
    textColorHover: '#3B82F6', 
  },
  Input: {
    color: 'rgba(255, 255, 255, 0.04)',
    colorFocus: 'rgba(255, 255, 255, 0.04)',
    border: '1px solid rgba(255, 255, 255, 0.09)',
    borderHover: '1px solid #3B82F6',
    borderFocus: '1px solid #3B82F6',
    boxShadowFocus: '0 0 0 2px rgba(59, 130, 246, 0.2)',
    borderRadius: '10px',
  },
  Tabs: {
    tabTextColorLine: 'rgba(255, 255, 255, 0.52)',
    tabTextColorActiveLine: 'rgba(255, 255, 255, 0.9)',
    tabTextColorHoverLine: 'rgba(255, 255, 255, 0.82)',
    barColor: '#3B82F6',
  },
  Tag: {
    borderPrimary: '1px solid rgba(59, 130, 246, 0.5)',
    textColorPrimary: '#60A5FA',
    colorPrimary: 'rgba(59, 130, 246, 0.1)',

    borderSuccess: '1px solid rgba(16, 185, 129, 0.5)',
    textColorSuccess: '#34D399',
    colorSuccess: 'rgba(16, 185, 129, 0.1)',

    borderWarning: '1px solid rgba(245, 158, 11, 0.5)',
    textColorWarning: '#FBBF24',
    colorWarning: 'rgba(245, 158, 11, 0.1)',

    borderError: '1px solid rgba(239, 68, 68, 0.5)',
    textColorError: '#F87171',
    colorError: 'rgba(239, 68, 68, 0.1)',
  },
  Message: {
    color: '#26262a',
    textColor: 'rgba(255, 255, 255, 0.82)',
    boxShadow: '0 4px 16px rgba(0, 0, 0, 0.6)',
  },
  Notification: {
    color: '#26262a',
    textColor: 'rgba(255, 255, 255, 0.82)',
    headerTextColor: 'rgba(255, 255, 255, 0.9)',
    closeIconColor: 'rgba(255, 255, 255, 0.52)',
    closeIconColorHover: 'rgba(255, 255, 255, 0.9)',
    boxShadow: '0 4px 16px rgba(0, 0, 0, 0.6)',
  },
};

export const lightIndustrialThemeOverrides: GlobalThemeOverrides = {
  common: {
    bodyColor: '#f6f9f8',
    cardColor: '#ffffff',
    modalColor: '#ffffff',
    popoverColor: '#ffffff',

    textColorBase: '#333639',
    textColor1: '#1f2225',
    textColor2: '#333639',
    textColor3: '#9999a3',

    borderColor: '#efeff5',
    dividerColor: '#efeff5',

    hoverColor: '#f3f3f5',
    pressedColor: '#eaeaec',

    primaryColor: '#3B82F6',
    primaryColorHover: '#60A5FA',
    primaryColorPressed: '#2563EB',
    primaryColorSuppl: '#3B82F6',

    borderRadius: '10px',
  },
  Card: {
    borderColor: '#efeff5',
    colorTarget: 'bg',
  },
  Menu: {
    itemColorActive: 'rgba(59, 130, 246, 0.1)',
    itemColorActiveHover: 'rgba(59, 130, 246, 0.15)',
    itemTextColorActive: '#3B82F6',
    itemIconColorActive: '#3B82F6',
  },
  InternalSelectMenu: {
    color: '#FFFFFF',
    optionColorPending: '#f3f3f5',
    optionTextColorActive: '#3B82F6',
    optionTextColorPressed: '#3B82F6',
  },
  DataTable: {
    thColor: '#fafafc',
    thTextColor: '#1f2225',
    tdColor: '#FFFFFF',
    tdColorHover: '#f3f3f5',
    borderColor: '#efeff5',
  },
  Input: {
    color: '#FFFFFF',
    colorFocus: '#FFFFFF',
    border: '1px solid #efeff5',
    borderHover: '1px solid #3B82F6',
    borderFocus: '1px solid #3B82F6',
    boxShadowFocus: '0 0 0 2px rgba(59, 130, 246, 0.2)',
    borderRadius: '10px',
  },
  Tabs: {
    tabTextColorLine: '#9999a3',
    tabTextColorActiveLine: '#1f2225',
    tabTextColorHoverLine: '#333639',
    barColor: '#3B82F6',
  },
  Tag: {
    borderPrimary: '1px solid rgba(59, 130, 246, 0.35)',
    textColorPrimary: '#2563EB',
    colorPrimary: 'rgba(59, 130, 246, 0.08)',

    borderSuccess: '1px solid rgba(16, 185, 129, 0.35)',
    textColorSuccess: '#047857',
    colorSuccess: 'rgba(16, 185, 129, 0.08)',

    borderWarning: '1px solid rgba(245, 158, 11, 0.35)',
    textColorWarning: '#B45309',
    colorWarning: 'rgba(245, 158, 11, 0.10)',

    borderError: '1px solid rgba(239, 68, 68, 0.35)',
    textColorError: '#B91C1C',
    colorError: 'rgba(239, 68, 68, 0.08)',
  },
  Message: {
    color: '#FFFFFF',
    textColor: '#333639',
    boxShadow: '0 8px 20px rgba(0, 0, 0, 0.08)',
  },
  Notification: {
    color: '#FFFFFF',
    textColor: '#333639',
    headerTextColor: '#1f2225',
    closeIconColor: '#9999a3',
    closeIconColorHover: '#333639',
    boxShadow: '0 8px 20px rgba(0, 0, 0, 0.08)',
  },
};

const setting = {
  //深色主题
  darkTheme: true,
  //系统主题色 (改为 Soybean 蓝)
  appTheme: '#3b82f6',
  //系统内置主题色列表
  appThemeList,
};

export default setting;
