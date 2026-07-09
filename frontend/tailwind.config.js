/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{vue,js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        'fire-red': '#DC143C',
        'fire-dark': '#191970',
        'fire-primary': '#DC143C',
        'fire-secondary': '#191970'
      }
    },
  },
  plugins: [],
}